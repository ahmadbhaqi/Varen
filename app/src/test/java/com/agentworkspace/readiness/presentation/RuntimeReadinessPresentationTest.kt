package com.agentworkspace.readiness.presentation

import com.agentworkspace.readiness.domain.ReadinessAction
import com.agentworkspace.readiness.domain.ReadinessBlocker
import com.agentworkspace.readiness.domain.RuntimeLimitation
import com.agentworkspace.readiness.domain.RuntimeReadiness
import com.agentworkspace.readiness.domain.WorkspaceCapabilityProfiles
import com.agentworkspace.readiness.domain.WorkspaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeReadinessPresentationTest {
    @Test
    fun `blocked model state gives one clear corrective action`() {
        val model = runtimeReadinessCard(
            RuntimeReadiness.Blocked(
                ReadinessBlocker(
                    code = "MODEL_REQUIRED",
                    message = "Select a model for this project.",
                    action = ReadinessAction.SELECT_MODEL,
                ),
            ),
        )

        assertEquals(ReadinessCardTone.ACTION_REQUIRED, model.tone)
        assertEquals("Choose a model", model.title)
        assertEquals("Choose model", model.actionLabel)
        assertEquals(ReadinessAction.SELECT_MODEL, model.action)
    }

    @Test
    fun `local SAF ready state explains the command boundary`() {
        val model = runtimeReadinessCard(
            RuntimeReadiness.Ready(
                profile = WorkspaceCapabilityProfiles.forKind(WorkspaceKind.LOCAL_SAF),
                limitations = listOf(
                    RuntimeLimitation(
                        code = "COMMAND_EXECUTION_UNAVAILABLE",
                        message = "Android SAF workspaces do not provide local command execution.",
                    ),
                ),
            ),
        )

        assertEquals(ReadinessCardTone.LIMITED, model.tone)
        assertEquals("Ready with limits", model.title)
        assertTrue(model.detail.contains("Shell commands are unavailable"))
        assertNull(model.action)
        assertNull(model.actionLabel)
    }

    @Test
    fun `github ready state highlights remote verification`() {
        val model = runtimeReadinessCard(
            RuntimeReadiness.Ready(
                profile = WorkspaceCapabilityProfiles.forKind(WorkspaceKind.GITHUB),
            ),
        )

        assertEquals(ReadinessCardTone.READY, model.tone)
        assertEquals("Ready to work", model.title)
        assertTrue(model.detail.contains("Remote verification"))
    }
}
