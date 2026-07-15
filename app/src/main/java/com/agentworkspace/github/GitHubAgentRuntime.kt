package com.agentworkspace.github

import com.agentworkspace.agent.runtime.AgentEvent
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.HistoryType
import com.agentworkspace.data.model.StepStatus
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.data.model.TaskUsage
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.data.model.UsageRecord
import com.agentworkspace.data.repository.HistoryRepository
import com.agentworkspace.data.repository.TaskRepository
import com.agentworkspace.data.repository.UsageRepository
import com.agentworkspace.model.api.ChatCompletionRequest
import com.agentworkspace.model.api.ChatMessage
import com.agentworkspace.model.api.LlmApiClient
import com.agentworkspace.readiness.domain.ToolCapabilityPolicy
import com.agentworkspace.readiness.domain.WorkspaceCapability
import com.agentworkspace.trust.policy.AgentAction
import com.agentworkspace.trust.policy.ApprovalDecision
import com.agentworkspace.trust.policy.TrustPolicy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubAgentRuntime @Inject constructor(
    private val taskRepository: TaskRepository,
    private val usageRepository: UsageRepository,
    private val historyRepository: HistoryRepository,
    private val llmApiClient: LlmApiClient,
    private val trustPolicy: TrustPolicy,
    private val githubTools: GitHubToolRegistry,
    private val remoteRepository: GitHubRemoteRepository,
) {
    private val _events = MutableSharedFlow<AgentEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }

    private companion object {
        const val MAX_ITERATIONS = 40
        const val MAX_TOOL_CALLS_PER_TURN = 12
    }

    suspend fun executeTask(
        task: Task,
        connection: com.agentworkspace.data.model.Connection,
        modelId: String,
        project: GitHubRemoteProject,
        trustMode: TrustMode,
        capabilities: Set<WorkspaceCapability>,
        approve: suspend (AgentAction, ApprovalDecision) -> Boolean,
    ): Result<Task> {
        var currentTask = task.copy(
            status = TaskStatus.RUNNING_LOOP,
            modelId = modelId,
            connectionId = connection.id,
        )
        taskRepository.updateTask(currentTask)
        record(currentTask, HistoryType.TASK_STARTED, "GitHub task started: ${task.title}")

        val workingBranch = runCatching {
            remoteRepository.createWorkingBranch(project, task.id)
        }.getOrElse { err ->
            currentTask = currentTask.copy(status = TaskStatus.FAILED, warnings = currentTask.warnings + (err.message ?: "GitHub branch setup failed"))
            taskRepository.updateTask(currentTask)
            _events.emit(AgentEvent.TaskFailed(currentTask.id, err.message ?: "GitHub branch setup failed"))
            return Result.failure(err)
        }

        _events.emit(AgentEvent.Message(currentTask.id, "Working branch: $workingBranch"))
        record(currentTask, HistoryType.CONNECTION_EVENT, "GitHub branch ready: $workingBranch")

        val messages = mutableListOf(
            ChatMessage(role = "system", content = buildSystemPrompt(project, workingBranch, trustMode)),
            ChatMessage(role = "user", content = task.goal),
        )
        val tools = githubTools.schemas(capabilities)

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
                val calls = aggCalls.toSortedMap().map { (index, pair) ->
                    com.agentworkspace.model.api.ToolCallResponse(
                        id = "call_$index",
                        function = com.agentworkspace.model.api.ToolCallFunctionResponse(pair.first, pair.second),
                    )
                }
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
                    filesRead = calls.count { it.function.name == "read_file" || it.function.name == "list_files" },
                    filesModified = calls.count { it.function.name == "write_file" || it.function.name == "edit_file" || it.function.name == "delete_file" },
                    searchCount = calls.count { it.function.name == "search_files" },
                )
                currentTask = currentTask.copy(usage = currentTask.usage + usageRecord)
                taskRepository.updateTask(currentTask)

                messages.add(
                    ChatMessage(
                        role = "assistant",
                        content = msgContent,
                        toolCalls = calls.map {
                            com.agentworkspace.model.api.ToolCallRequest(
                                id = it.id,
                                type = "function",
                                function = com.agentworkspace.model.api.ToolCallFunction(
                                    name = it.function.name,
                                    arguments = it.function.arguments,
                                ),
                            )
                        },
                    ),
                )

                if (calls.isEmpty()) {
                    currentTask = buildAndComplete(
                        currentTask,
                        msgContent,
                        project,
                        workingBranch,
                        capabilities,
                    )
                    return Result.success(currentTask)
                }

                if (calls.size > MAX_TOOL_CALLS_PER_TURN) {
                    _events.emit(AgentEvent.Error(currentTask.id, "Too many tool calls in one turn (${calls.size}); running first $MAX_TOOL_CALLS_PER_TURN."))
                }

                for (call in calls.take(MAX_TOOL_CALLS_PER_TURN)) {
                    val toolName = call.function.name
                    val argsRaw = call.function.arguments
                    if (
                        ToolCapabilityPolicy.isBuiltIn(toolName) &&
                        !ToolCapabilityPolicy.supports(toolName, capabilities)
                    ) {
                        val unavailable = "{\"error\":\"Tool is unavailable for this workspace: $toolName\"}"
                        _events.emit(AgentEvent.Error(currentTask.id, "Unavailable tool requested: $toolName"))
                        messages.add(toolResultMessage(call.id, toolName, unavailable))
                        continue
                    }
                    val args = (runCatching { json.parseToJsonElement(argsRaw) }.getOrNull() as? JsonObject)
                        ?: JsonObject(emptyMap())
                    val action = githubTools.actionFor(toolName, args)

                    val decision = trustPolicy.requiresApproval(trustMode, action)
                    if (decision.requiresApproval) {
                        currentTask = currentTask.copy(status = TaskStatus.WAITING_APPROVAL)
                        taskRepository.updateTask(currentTask)
                        _events.emit(AgentEvent.ApprovalRequired(currentTask.id, action, decision))
                        val approved = approve(action, decision)
                        _events.emit(AgentEvent.ApprovalResolved(currentTask.id, action, approved))
                        if (!approved) {
                            messages.add(toolResultMessage(call.id, toolName, "{\"denied\":\"user denied this GitHub action\"}"))
                            record(currentTask, HistoryType.APPROVAL_DENIED, "Denied GitHub tool: $toolName", success = false)
                            continue
                        }
                        record(currentTask, HistoryType.APPROVAL_GRANTED, "Approved GitHub tool: $toolName")
                    }

                    currentTask = currentTask.copy(status = TaskStatus.EDITING)
                    taskRepository.updateTask(currentTask)
                    _events.emit(AgentEvent.ToolCallStarted(currentTask.id, call.id, toolName, argsRaw))
                    val resultStr = githubTools.execute(toolName, argsRaw, project, workingBranch, task.projectId, currentTask.id)
                    _events.emit(AgentEvent.ToolCallFinished(currentTask.id, call.id, toolName, resultStr))
                    messages.add(toolResultMessage(call.id, toolName, resultStr))
                    currentTask = currentTask.copy(
                        toolCalls = currentTask.toolCalls + com.agentworkspace.data.model.ToolCall(
                            toolName = "github.$toolName",
                            input = argsRaw,
                            output = resultStr,
                        ),
                    )
                }
            }

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
                warnings = currentTask.warnings + (e.message ?: "Unknown GitHub runtime error"),
            )
            taskRepository.updateTask(currentTask)
            record(currentTask, HistoryType.TASK_FAILED, "GitHub task failed: ${e.message}", success = false)
            _events.emit(AgentEvent.TaskFailed(currentTask.id, e.message ?: "Unknown GitHub runtime error"))
            return Result.failure(e)
        }
    }

    private suspend fun buildAndComplete(
        task: Task,
        summary: String,
        project: GitHubRemoteProject,
        branch: String,
        capabilities: Set<WorkspaceCapability>,
    ): Task {
        if (WorkspaceCapability.REMOTE_VERIFICATION !in capabilities) {
            val current = task.copy(
                status = TaskStatus.COMPLETED,
                outputSummary = listOf(summary, "Remote verification was unavailable for this run.")
                    .filter(String::isNotBlank)
                    .joinToString("\n"),
            )
            taskRepository.updateTask(current)
            record(current, HistoryType.TASK_COMPLETED, "GitHub task completed without remote verification", success = true)
            _events.emit(AgentEvent.TaskComplete(current.id))
            return current
        }
        var current = task.copy(status = TaskStatus.EXECUTING)
        taskRepository.updateTask(current)
        _events.emit(AgentEvent.Message(current.id, "Building APK with GitHub Actions..."))
        record(current, HistoryType.COMMAND_EXECUTION, "GitHub Actions APK build started on $branch")

        val build = remoteRepository.buildApk(project, branch)
        val finalSummary = buildString {
            if (summary.isNotBlank()) appendLine(summary)
            appendLine("GitHub branch: ${build.branch}")
            appendLine("Build run: ${build.runUrl}")
            append("APK path: ${build.apkPath}")
        }.trim()
        current = current.copy(
            status = TaskStatus.COMPLETED,
            outputSummary = finalSummary,
        )
        taskRepository.updateTask(current)
        _events.emit(
            AgentEvent.VerificationSucceeded(
                taskId = current.id,
                method = "github_actions_apk",
                detail = build.runUrl,
            ),
        )
        record(current, HistoryType.TASK_COMPLETED, "GitHub task completed. APK: ${build.apkPath}", success = true)
        _events.emit(AgentEvent.Message(current.id, "APK ready: ${build.apkPath}"))
        _events.emit(AgentEvent.TaskComplete(current.id))
        return current
    }

    private fun buildSystemPrompt(
        project: GitHubRemoteProject,
        branch: String,
        trustMode: TrustMode,
    ): String = """
        You are Varen, an autonomous coding agent editing a GitHub repository remotely.

        Repository: ${project.fullName}
        Base branch: ${project.baseBranch}
        Working branch: $branch

        How you must work:
        1. Read relevant repository files before editing; never guess at content you have not read.
        2. Use repository-relative paths only. Do not use /sdcard, local device paths, or content:// paths.
        3. Make focused changes with edit_file when possible.
        4. Do not run builds yourself. After your final answer, the app will automatically run GitHub Actions and download the APK artifact.
        5. Keep going until the requested code change is complete.

        Trust mode: $trustMode. Riskier GitHub write actions may require the user's approval.
    """.trimIndent()

    private fun toolResultMessage(toolCallId: String, name: String, content: String) =
        ChatMessage(role = "tool", content = content, toolCallId = toolCallId, name = name)

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
        filesRead: Int = 0,
        filesModified: Int = 0,
        searchCount: Int = 0,
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
            latencyMs = latencyMs,
            filesRead = filesRead,
            filesModified = filesModified,
            searchCount = searchCount,
        )
        runCatching { usageRepository.recordUsage(record) }
        return record
    }

    private suspend fun record(task: Task, type: HistoryType, description: String, success: Boolean = true) {
        historyRepository.recordHistory(
            HistoryEntry(projectId = task.projectId, taskId = task.id, type = type, description = description, success = success),
        )
    }

    private fun estimateTokens(chars: Int): Int = (chars / 4).coerceAtLeast(0)

    private data class ReportedUsage(
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
        val cachedTokens: Int? = null,
        val reasoningTokens: Int? = null,
    )

    private fun parseReportedUsage(raw: String): ReportedUsage? {
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val promptDetails = obj["prompt_tokens_details"] as? JsonObject
        val completionDetails = obj["completion_tokens_details"] as? JsonObject
        return ReportedUsage(
            inputTokens = obj.int("prompt_tokens") ?: obj.int("input_tokens"),
            outputTokens = obj.int("completion_tokens") ?: obj.int("output_tokens"),
            cachedTokens = promptDetails?.int("cached_tokens") ?: obj.int("cached_tokens"),
            reasoningTokens = completionDetails?.int("reasoning_tokens") ?: obj.int("reasoning_tokens"),
        )
    }

    private fun JsonObject.int(name: String): Int? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private operator fun TaskUsage.plus(record: UsageRecord): TaskUsage = copy(
        requests = requests + record.requests,
        inputTokens = inputTokens + record.inputTokens,
        outputTokens = outputTokens + record.outputTokens,
        cachedTokens = cachedTokens + record.cachedTokens,
        reasoningTokens = reasoningTokens + record.reasoningTokens,
        toolCalls = toolCalls + record.toolCalls,
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
