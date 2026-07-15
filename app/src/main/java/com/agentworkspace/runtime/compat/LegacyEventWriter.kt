package com.agentworkspace.runtime.compat

import com.agentworkspace.agent.runtime.AgentEvent
import com.agentworkspace.runtime.data.RunTimelineStore
import com.agentworkspace.runtime.domain.RunEventKind
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LegacyEventWriter @Inject constructor(
    private val timeline: RunTimelineStore,
) {
    suspend fun collect(
        runId: String,
        taskId: String,
        events: Flow<AgentEvent>,
    ) {
        val assistantBuffer = StringBuilder()

        suspend fun flushAssistant() {
            if (assistantBuffer.isEmpty()) return
            val content = assistantBuffer.toString()
            assistantBuffer.clear()
            timeline.appendMessage(runId, "assistant", content)
            timeline.appendEvent(
                runId,
                RunEventKind.MESSAGE,
                buildJsonObject {
                    put("role", "assistant")
                    put("content", content)
                }.toString(),
            )
        }

        try {
            events.takeWhile { event ->
                if (event.taskId() != taskId) return@takeWhile true
                when (event) {
                    is AgentEvent.Message -> {
                        assistantBuffer.append(event.content)
                        if (assistantBuffer.length >= MAX_BUFFER_CHARS) flushAssistant()
                    }
                    is AgentEvent.ToolCallStarted -> {
                        flushAssistant()
                        timeline.appendEvent(
                            runId,
                            RunEventKind.TOOL_STARTED,
                            buildJsonObject {
                                put("callId", event.callId)
                                put("tool", event.tool)
                                put("args", event.args)
                            }.toString(),
                        )
                    }
                    is AgentEvent.ToolCallFinished -> {
                        flushAssistant()
                        timeline.appendEvent(
                            runId,
                            RunEventKind.TOOL_FINISHED,
                            buildJsonObject {
                                put("callId", event.callId)
                                put("tool", event.tool)
                                put("result", event.result)
                            }.toString(),
                        )
                    }
                    is AgentEvent.Iteration -> {
                        flushAssistant()
                        timeline.appendEvent(
                            runId,
                            RunEventKind.ITERATION,
                            buildJsonObject { put("iteration", event.iteration) }.toString(),
                        )
                    }
                    is AgentEvent.Error -> {
                        flushAssistant()
                        timeline.appendEvent(
                            runId,
                            RunEventKind.FAILED,
                            buildJsonObject { put("message", event.message) }.toString(),
                        )
                    }
                    is AgentEvent.TaskFailed -> {
                        flushAssistant()
                        timeline.appendEvent(
                            runId,
                            RunEventKind.FAILED,
                            buildJsonObject { put("message", event.error) }.toString(),
                        )
                    }
                    is AgentEvent.VerificationSucceeded -> {
                        flushAssistant()
                        timeline.appendEvent(
                            runId,
                            RunEventKind.VERIFICATION_SUCCEEDED,
                            buildJsonObject {
                                put("method", event.method)
                                put("detail", event.detail)
                            }.toString(),
                        )
                    }
                    is AgentEvent.TaskComplete -> flushAssistant()
                    is AgentEvent.ApprovalRequired,
                    is AgentEvent.ApprovalResolved,
                    -> flushAssistant()
                }
                event !is AgentEvent.TaskComplete && event !is AgentEvent.TaskFailed
            }.collect {}
        } finally {
            flushAssistant()
        }
    }

    private fun AgentEvent.taskId(): String = when (this) {
        is AgentEvent.Message -> taskId
        is AgentEvent.ToolCallStarted -> taskId
        is AgentEvent.ToolCallFinished -> taskId
        is AgentEvent.ApprovalRequired -> taskId
        is AgentEvent.ApprovalResolved -> taskId
        is AgentEvent.VerificationSucceeded -> taskId
        is AgentEvent.TaskComplete -> taskId
        is AgentEvent.TaskFailed -> taskId
        is AgentEvent.Error -> taskId
        is AgentEvent.Iteration -> taskId
    }

    private companion object {
        const val MAX_BUFFER_CHARS = 1_024
    }
}
