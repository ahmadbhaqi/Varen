package com.agentworkspace.agent.runtime

import android.net.Uri
import com.agentworkspace.agent.tools.ToolRegistry
import com.agentworkspace.mcp.McpToolProvider
import com.agentworkspace.checkpoint.CheckpointManager
import com.agentworkspace.data.model.AgentStep
import com.agentworkspace.data.model.DiffEntry
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.HistoryType
import com.agentworkspace.data.model.StepStatus
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.data.model.TaskUsage
import com.agentworkspace.data.model.UsageRecord
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.data.repository.DiffRepository
import com.agentworkspace.data.repository.HistoryRepository
import com.agentworkspace.data.repository.TaskRepository
import com.agentworkspace.data.repository.UsageRepository
import com.agentworkspace.execution.ExecutionLayer
import com.agentworkspace.model.api.ChatCompletionRequest
import com.agentworkspace.model.api.ChatMessage
import com.agentworkspace.model.api.LlmApiClient
import com.agentworkspace.trust.policy.AgentAction
import com.agentworkspace.trust.policy.ApprovalDecision
import com.agentworkspace.trust.policy.TrustPolicy
import com.agentworkspace.diff.engine.DiffEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The agent runtime: the core of the product.
 *
 * A real tool-calling loop, not a one-shot chatbot:
 *   build messages + tools -> call the model -> parse tool_calls ->
 *   trust/approve -> snapshot before mutation -> dispatch tool ->
 *   feed result back -> repeat, until the model returns a final answer.
 *
 * Every phase emits an [AgentEvent] so the UI can render thoughts, tool calls,
 * results and approval prompts in realtime. Safety is non-negotiable:
 * destructive actions require approval, and every mutation is preceded by a
 * checkpoint that can be rolled back.
 */
@Singleton
class AgentRuntime @Inject constructor(
    private val taskRepository: TaskRepository,
    private val usageRepository: UsageRepository,
    private val historyRepository: HistoryRepository,
    private val diffRepository: DiffRepository,
    private val checkpointManager: CheckpointManager,
    private val executionLayer: ExecutionLayer,
    private val llmApiClient: LlmApiClient,
    private val trustPolicy: TrustPolicy,
    private val toolRegistry: ToolRegistry,
    private val mcpToolProvider: McpToolProvider,
) {
    private val _events = MutableSharedFlow<AgentEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }

    /** Hard limits so a runaway loop can never burn unbounded resources. */
    private companion object {
        const val MAX_ITERATIONS = 40
        const val MAX_TOOL_CALLS_PER_TURN = 12
    }

    /**
     * Execute a task end-to-end via the tool-calling loop.
     *
     * @param task the task to run
     * @param connection the authenticated connection to use
     * @param modelId the model id to call
     * @param treeUri the SAF tree Uri of the open workspace root
     * @param workingDir a filesystem path for shell commands (may differ from treeUri)
     * @param trustMode how much autonomy the agent has
     * @param approve a suspend callback that resolves to true when the user
     *   approves a pending action; the UI supplies this so approval is async
     *   and cancellable.
     */
    suspend fun executeTask(
        task: Task,
        connection: com.agentworkspace.data.model.Connection,
        modelId: String,
        treeUri: Uri,
        workingDir: String,
        trustMode: TrustMode,
        approve: suspend (AgentAction, ApprovalDecision) -> Boolean,
    ): Result<Task> {
        var currentTask = task.copy(
            status = TaskStatus.RUNNING_LOOP,
            modelId = modelId,
            connectionId = connection.id,
        )
        taskRepository.updateTask(currentTask)
        record(currentTask, HistoryType.TASK_STARTED, "Task started: ${task.title}")

        val messages = mutableListOf<ChatMessage>(
            ChatMessage(role = "system", content = buildSystemPrompt(trustMode)),
            ChatMessage(role = "user", content = task.goal),
        )
        val tools = toolRegistry.schemas() + mcpToolProvider.schemas()
        try {
            var iteration = 0
            while (iteration < MAX_ITERATIONS) {
                iteration++
                _events.emit(AgentEvent.Iteration(currentTask.id, iteration))

                val start = System.currentTimeMillis()
                val requestInputChars = messages.sumOf { it.content.length } +
                    tools.sumOf { it.function.name.length + it.function.description.length }
                val request = ChatCompletionRequest(
                    model = modelId,
                    messages = messages,
                    tools = tools,
                    temperature = 0.2,
                    stream = true,
                )

                // Stream the response: emit text deltas live so the UI shows
                // the agent thinking token-by-token, then assemble the final
                // assistant turn + tool calls from the aggregated result.
                val sb = StringBuilder()
                val aggCalls = mutableMapOf<Int, Pair<String, String>>()
                var reportedUsage: ReportedUsage? = null
                llmApiClient.streamChat(connection, request).collect { delta ->
                    when (delta) {
                        is com.agentworkspace.model.api.StreamDelta.Text -> {
                            sb.append(delta.text)
                            _events.emit(AgentEvent.Message(currentTask.id, delta.text))
                        }
                        is com.agentworkspace.model.api.StreamDelta.ToolCall -> {
                            val cur = aggCalls.getOrPut(delta.index) { "" to "" }
                            val nm = delta.name ?: cur.first
                            val args = cur.second + delta.argumentsChunk
                            aggCalls[delta.index] = nm to args
                        }
                        is com.agentworkspace.model.api.StreamDelta.Usage -> {
                            reportedUsage = parseReportedUsage(delta.raw) ?: reportedUsage
                        }
                        is com.agentworkspace.model.api.StreamDelta.Done -> {
                            if (sb.isEmpty() && delta.content.isNotEmpty()) sb.append(delta.content)
                            delta.toolCalls.forEach { aggCalls[it.index] = it.name to it.arguments }
                        }
                    }
                }
                val latency = System.currentTimeMillis() - start
                val msgContent = sb.toString().trim()
                val calls = aggCalls.toSortedMap().map { (i, p) ->
                    com.agentworkspace.model.api.ToolCallResponse(
                        id = "call_$i",
                        function = com.agentworkspace.model.api.ToolCallFunctionResponse(name = p.first, arguments = p.second),
                    )
                }
                val toolUsage = summarizeToolUsage(calls.map { it.function.name })
                messages.add(
                    ChatMessage(
                        role = "assistant",
                        content = msgContent,
                        toolCalls = calls.map { com.agentworkspace.model.api.ToolCallRequest(it.id, "function", com.agentworkspace.model.api.ToolCallFunction(it.function.name, it.function.arguments)) },
                    ),
                )

                val usageRecord = recordUsage(
                    task = currentTask,
                    modelId = modelId,
                    connectionId = connection.id,
                    requests = 1,
                    toolCalls = calls.size,
                    latencyMs = latency,
                    inputTokens = reportedUsage?.inputTokens ?: estimateTokens(requestInputChars),
                    outputTokens = reportedUsage?.outputTokens ?: estimateTokens(
                        msgContent.length + calls.sumOf { it.function.name.length + it.function.arguments.length },
                    ),
                    cachedTokens = reportedUsage?.cachedTokens ?: 0,
                    reasoningTokens = reportedUsage?.reasoningTokens ?: 0,
                    executionCount = toolUsage.executionCount,
                    filesRead = toolUsage.filesRead,
                    filesModified = toolUsage.filesModified,
                    searchCount = toolUsage.searchCount,
                    diffCount = toolUsage.diffCount,
                    checkpointCount = toolUsage.checkpointCount,
                )
                currentTask = currentTask.copy(usage = currentTask.usage + usageRecord)
                taskRepository.updateTask(currentTask)

                if (calls.isEmpty()) {
                    // No tool calls => final answer.
                    currentTask = currentTask.copy(
                        status = TaskStatus.COMPLETED,
                        outputSummary = msgContent,
                    )
                    taskRepository.updateTask(currentTask)
                    record(currentTask, HistoryType.TASK_COMPLETED, "Task completed", success = true)
                    _events.emit(AgentEvent.TaskComplete(currentTask.id))
                    return Result.success(currentTask)
                }

                if (calls.size > MAX_TOOL_CALLS_PER_TURN) {
                    _events.emit(AgentEvent.Error(currentTask.id, "Too many tool calls in one turn (${calls.size}); running first $MAX_TOOL_CALLS_PER_TURN."))
                }

                // Execute each tool call in order.
                for (call in calls.take(MAX_TOOL_CALLS_PER_TURN)) {
                    val toolName = call.function.name
                    val argsRaw = call.function.arguments
                    val args: kotlinx.serialization.json.JsonObject =
                        (runCatching { json.parseToJsonElement(argsRaw) }.getOrNull() as? kotlinx.serialization.json.JsonObject)
                            ?: kotlinx.serialization.json.JsonObject(emptyMap())
                    val action = toolRegistry.actionFor(toolName, args)

                    // Trust gate.
                    val decision = trustPolicy.requiresApproval(trustMode, action)
                    if (decision.requiresApproval) {
                        currentTask = currentTask.copy(status = TaskStatus.WAITING_APPROVAL)
                        taskRepository.updateTask(currentTask)
                        _events.emit(AgentEvent.ApprovalRequired(currentTask.id, action, decision))
                        val approved = approve(action, decision)
                        _events.emit(AgentEvent.ApprovalResolved(currentTask.id, action, approved))
                        if (!approved) {
                            messages.add(toolResultMessage(call.id, toolName, "{\"denied\":\"user denied this action\"}"))
                            record(currentTask, HistoryType.APPROVAL_DENIED, "Denied: $toolName", success = false)
                            continue
                        }
                        record(currentTask, HistoryType.APPROVAL_GRANTED, "Approved: $toolName")
                    }
                    currentTask = currentTask.copy(status = TaskStatus.EDITING)
                    taskRepository.updateTask(currentTask)

                    // Snapshot before any mutation: read the REAL current content so a
                    // checkpoint can restore it and a diff can be produced afterwards.
                    val isMutation = action is AgentAction.WriteFile || action is AgentAction.DeleteFile
                    val mutatedPath = if (isMutation) {
                        (args["path"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }) ?: ""
                    } else ""
                    var beforeContent: String? = null
                    if (isMutation && mutatedPath.isNotEmpty()) {
                        beforeContent = runCatching { toolRegistry.readFileContent(treeUri, mutatedPath) }.getOrNull()
                        runCatching {
                            checkpointManager.createBeforeEdit(
                                projectId = task.projectId, taskId = currentTask.id,
                                files = listOf(mutatedPath to (beforeContent ?: "")), reason = "Before $toolName",
                            )
                        }
                    }

                    _events.emit(AgentEvent.ToolCallStarted(currentTask.id, call.id, toolName, argsRaw))

                    // Dispatch.
                    val resultStr = when (toolName) {
                        "run_command" -> {
                            val cmd = (args["command"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }) ?: ""
                            val cwd = (args["cwd"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }) ?: workingDir
                            val er = executionLayer.execute(cmd, cwd, task.projectId, currentTask.id)
                            if (!er.success && er.exitCode == -1) {
                                _events.emit(AgentEvent.Error(currentTask.id, er.stderr))
                            }
                            kotlinx.serialization.json.buildJsonObject {
                                put("exit_code", kotlinx.serialization.json.JsonPrimitive(er.exitCode))
                                put("success", kotlinx.serialization.json.JsonPrimitive(er.success))
                                put("stdout", kotlinx.serialization.json.JsonPrimitive(er.stdout.take(4000)))
                                put("stderr", kotlinx.serialization.json.JsonPrimitive(er.stderr.take(2000)))
                            }.toString()
                        }
                        else -> if (mcpToolProvider.owns(toolName)) {
                            mcpToolProvider.execute(toolName, argsRaw, task.projectId, currentTask.id)
                        } else {
                            toolRegistry.execute(toolName, argsRaw, treeUri, task.projectId, currentTask.id)
                        }
                    }

                    _events.emit(AgentEvent.ToolCallFinished(currentTask.id, call.id, toolName, resultStr))
                    messages.add(toolResultMessage(call.id, toolName, resultStr))

                    // Produce a reviewable diff for successful write/edit operations so
                    // the DiffViewer surface reflects real agent work, not sample data.
                    if (isMutation && mutatedPath.isNotEmpty() && (toolName == "write_file" || toolName == "edit_file")) {
                        runCatching {
                            val afterContent = toolRegistry.readFileContent(treeUri, mutatedPath) ?: ""
                            val patch = DiffEngine.generateDiff(beforeContent ?: "", afterContent, mutatedPath)
                            if (patch.isNotBlank()) {
                                diffRepository.createDiff(
                                    taskId = currentTask.id,
                                    projectId = task.projectId,
                                    filePath = mutatedPath,
                                    originalContent = beforeContent ?: "",
                                    newContent = afterContent,
                                    diffPatch = patch,
                                )
                                record(currentTask, HistoryType.FILE_WRITE, "Edited: $mutatedPath", success = true)
                            }
                        }
                    }

                    currentTask = currentTask.copy(
                        toolCalls = currentTask.toolCalls + com.agentworkspace.data.model.ToolCall(toolName = toolName, input = argsRaw, output = resultStr),
                    )
                }
            }

            // Exceeded iterations.
            currentTask = currentTask.copy(status = TaskStatus.FAILED, warnings = currentTask.warnings + "Max iterations reached")
            taskRepository.updateTask(currentTask)
            record(currentTask, HistoryType.TASK_FAILED, "Reached max iterations", success = false)
            _events.emit(AgentEvent.TaskFailed(currentTask.id, "Reached max iterations ($MAX_ITERATIONS)"))
            return Result.failure(Exception("Max iterations reached"))

        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            currentTask = currentTask.copy(
                status = TaskStatus.FAILED,
                warnings = currentTask.warnings + (e.message ?: "Unknown error"),
            )
            taskRepository.updateTask(currentTask)
            record(currentTask, HistoryType.TASK_FAILED, "Task failed: ${e.message}", success = false)
            _events.emit(AgentEvent.TaskFailed(currentTask.id, e.message ?: "Unknown error"))
            return Result.failure(e)
        }
    }

    private fun buildSystemPrompt(trustMode: TrustMode): String = """
        You are Varen, an autonomous coding agent running on an Android device inside a real project workspace. You operate on real files through tools.

        How you must work:
        1. Read relevant files before editing; never guess at content you have not read.
        2. Make focused, surgical changes; prefer edit_file over rewriting whole files.
        3. After editing, verify the result by reading the changed region or running a command.
        4. Keep going until the task is fully done, then give a concise summary.

        Trust mode: $trustMode. Riskier actions may require the user's approval.

        Tool results are returned as JSON strings. Paths must be relative to the workspace root unless a tool result gives you a content:// URI. Do not invent /sdcard or other device filesystem paths.
    """.trimIndent()

    private fun toolResultMessage(toolCallId: String, name: String, content: String) =
        com.agentworkspace.model.api.ChatMessage(
            role = "tool",
            content = content,
            toolCallId = toolCallId,
            name = name,
        )

    private suspend fun recordUsage(
        task: Task,
        modelId: String,
        connectionId: String,
        requests: Int,
        toolCalls: Int,
        latencyMs: Long,
        inputTokens: Int,
        outputTokens: Int,
        cachedTokens: Int = 0,
        reasoningTokens: Int = 0,
        executionCount: Int = 0,
        filesRead: Int = 0,
        filesModified: Int = 0,
        searchCount: Int = 0,
        diffCount: Int = 0,
        checkpointCount: Int = 0,
    ): UsageRecord {
        val record = UsageRecord(
            taskId = task.id,
            projectId = task.projectId,
            modelId = modelId,
            connectionId = connectionId,
            requests = requests,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedTokens = cachedTokens,
            reasoningTokens = reasoningTokens,
            toolCalls = toolCalls,
            executionCount = executionCount,
            filesRead = filesRead,
            filesModified = filesModified,
            searchCount = searchCount,
            diffCount = diffCount,
            checkpointCount = checkpointCount,
            latencyMs = latencyMs,
        )
        runCatching {
            usageRepository.recordUsage(record)
        }
        return record
    }

    private suspend fun record(task: Task, type: HistoryType, description: String, success: Boolean = true) {
        historyRepository.recordHistory(
            HistoryEntry(projectId = task.projectId, taskId = task.id, type = type, description = description, success = success),
        )
    }

    private fun estimateTokens(chars: Int): Int = (chars / 4).coerceAtLeast(0)

    private data class ToolUsageSummary(
        val executionCount: Int = 0,
        val filesRead: Int = 0,
        val filesModified: Int = 0,
        val searchCount: Int = 0,
        val diffCount: Int = 0,
        val checkpointCount: Int = 0,
    )

    private fun summarizeToolUsage(toolNames: List<String>): ToolUsageSummary {
        val filesModified = toolNames.count { it == "write_file" || it == "edit_file" || it == "delete_file" }
        return ToolUsageSummary(
            executionCount = toolNames.count { it == "run_command" },
            filesRead = toolNames.count { it == "read_file" || it == "list_files" },
            filesModified = filesModified,
            searchCount = toolNames.count { it == "search_files" },
            diffCount = toolNames.count { it == "write_file" || it == "edit_file" },
            checkpointCount = filesModified,
        )
    }

    private data class ReportedUsage(
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
        val cachedTokens: Int? = null,
        val reasoningTokens: Int? = null,
    )

    private fun parseReportedUsage(raw: String): ReportedUsage? {
        val obj = runCatching { json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject }.getOrNull()
            ?: return null
        val promptDetails = obj["prompt_tokens_details"] as? kotlinx.serialization.json.JsonObject
        val completionDetails = obj["completion_tokens_details"] as? kotlinx.serialization.json.JsonObject
        return ReportedUsage(
            inputTokens = obj.int("prompt_tokens") ?: obj.int("input_tokens"),
            outputTokens = obj.int("completion_tokens") ?: obj.int("output_tokens"),
            cachedTokens = promptDetails?.int("cached_tokens") ?: obj.int("cached_tokens"),
            reasoningTokens = completionDetails?.int("reasoning_tokens") ?: obj.int("reasoning_tokens"),
        )
    }

    private fun kotlinx.serialization.json.JsonObject.int(name: String): Int? =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()

    private operator fun TaskUsage.plus(record: UsageRecord): TaskUsage = copy(
        requests = requests + record.requests,
        inputTokens = inputTokens + record.inputTokens,
        outputTokens = outputTokens + record.outputTokens,
        cachedTokens = cachedTokens + record.cachedTokens,
        reasoningTokens = reasoningTokens + record.reasoningTokens,
        toolCalls = toolCalls + record.toolCalls,
        executionCount = executionCount + record.executionCount,
        filesRead = filesRead + record.filesRead,
        filesModified = filesModified + record.filesModified,
        searchCount = searchCount + record.searchCount,
        diffCount = diffCount + record.diffCount,
        checkpointCount = checkpointCount + record.checkpointCount,
        errors = errors + record.errors,
        retries = retries + record.retries,
        latencyMs = latencyMs + record.latencyMs,
    )
}

/** Events emitted by the agent runtime for UI consumption. */
sealed class AgentEvent {
    data class Iteration(val taskId: String, val iteration: Int) : AgentEvent()
    data class Message(val taskId: String, val content: String) : AgentEvent()
    data class ToolCallStarted(val taskId: String, val callId: String, val tool: String, val args: String) : AgentEvent()
    data class ToolCallFinished(val taskId: String, val callId: String, val tool: String, val result: String) : AgentEvent()
    data class ApprovalRequired(val taskId: String, val action: AgentAction, val decision: ApprovalDecision) : AgentEvent()
    data class ApprovalResolved(val taskId: String, val action: AgentAction, val approved: Boolean) : AgentEvent()
    data class TaskComplete(val taskId: String) : AgentEvent()
    data class TaskFailed(val taskId: String, val error: String) : AgentEvent()
    data class Error(val taskId: String, val message: String) : AgentEvent()
}
