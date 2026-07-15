package com.agentworkspace.runtime.service

import com.agentworkspace.runtime.domain.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunServicePolicyTest {
    @Test
    fun `wire actions have honest immediate statuses`() {
        assertEquals(RunStatus.QUEUED, RunServicePolicy.initialStatus(AgentRunService.ACTION_START))
        assertEquals(RunStatus.RECOVERING, RunServicePolicy.initialStatus(AgentRunService.ACTION_RESUME))
        assertEquals(RunStatus.PAUSED, RunServicePolicy.initialStatus(AgentRunService.ACTION_PAUSE))
        assertEquals(RunStatus.CANCELLED, RunServicePolicy.initialStatus(AgentRunService.ACTION_CANCEL))
    }

    @Test
    fun `service remains foreground while approval is pending but not after pause or terminal state`() {
        assertTrue(RunServicePolicy.keepsServiceActive(RunStatus.WAITING_APPROVAL))
        assertFalse(RunServicePolicy.keepsServiceActive(RunStatus.PAUSED))
        assertFalse(RunServicePolicy.keepsServiceActive(RunStatus.COMPLETED))
        assertFalse(RunServicePolicy.keepsServiceActive(RunStatus.CANCELLED))
    }
}
