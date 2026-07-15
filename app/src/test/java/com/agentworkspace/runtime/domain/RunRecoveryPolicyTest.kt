package com.agentworkspace.runtime.domain

import com.agentworkspace.data.db.entity.runtime.RunEventEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRecoveryPolicyTest {
    private val policy = RunRecoveryPolicy()

    @Test
    fun `unmatched tool start is an uncertain external outcome`() {
        assertTrue(policy.hasUncertainSideEffect(listOf(toolStarted(1, "call-1"))))
    }

    @Test
    fun `matched tool finish is safe to recover`() {
        assertFalse(
            policy.hasUncertainSideEffect(
                listOf(toolStarted(1, "call-1"), toolFinished(2, "call-1")),
            ),
        )
    }

    @Test
    fun `one unmatched call remains uncertain when another call completed`() {
        assertTrue(
            policy.hasUncertainSideEffect(
                listOf(
                    toolStarted(1, "call-1"),
                    toolFinished(2, "call-1"),
                    toolStarted(3, "call-2"),
                ),
            ),
        )
    }

    private fun toolStarted(sequence: Long, callId: String) =
        event(sequence, RunEventKind.TOOL_STARTED, callId)

    private fun toolFinished(sequence: Long, callId: String) =
        event(sequence, RunEventKind.TOOL_FINISHED, callId)

    private fun event(sequence: Long, kind: RunEventKind, callId: String) = RunEventEntity(
        id = "event-$sequence",
        runId = "run-1",
        sequence = sequence,
        kind = kind,
        payloadJson = "{\"callId\":\"$callId\"}",
        createdAt = sequence,
    )
}
