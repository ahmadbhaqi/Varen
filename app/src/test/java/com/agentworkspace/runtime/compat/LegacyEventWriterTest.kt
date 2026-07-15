package com.agentworkspace.runtime.compat

import com.agentworkspace.agent.runtime.AgentEvent
import com.agentworkspace.runtime.data.RunTimelineStore
import com.agentworkspace.runtime.domain.RunEventKind
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyEventWriterTest {
    @Test
    fun `assistant deltas are coalesced and tool events are persisted`() = runTest {
        val store = RecordingTimelineStore()
        val writer = LegacyEventWriter(store)

        writer.collect(
            runId = "run-1",
            taskId = "task-1",
            events = flowOf(
                AgentEvent.Message("task-1", "Hello "),
                AgentEvent.Message("task-1", "world"),
                AgentEvent.ToolCallStarted("task-1", "call-1", "read_file", "{\"path\":\"README.md\"}"),
                AgentEvent.ToolCallFinished("task-1", "call-1", "read_file", "ok"),
                AgentEvent.Message("another-task", "ignored"),
            ),
        )

        assertEquals(listOf("assistant" to "Hello world"), store.messages)
        assertEquals(
            listOf(RunEventKind.MESSAGE, RunEventKind.TOOL_STARTED, RunEventKind.TOOL_FINISHED),
            store.events.map { it.first },
        )
    }

    private class RecordingTimelineStore : RunTimelineStore {
        val events = mutableListOf<Pair<RunEventKind, String>>()
        val messages = mutableListOf<Pair<String, String>>()

        override suspend fun appendEvent(runId: String, kind: RunEventKind, payloadJson: String) {
            events += kind to payloadJson
        }

        override suspend fun appendMessage(
            runId: String,
            role: String,
            content: String,
            toolCallId: String?,
        ) {
            messages += role to content
        }
    }
}
