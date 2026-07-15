package com.agentworkspace.runtime.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RunServiceIntentTest {
    @Test
    fun `service actions have stable wire values`() {
        assertEquals("com.agentworkspace.runtime.START", AgentRunService.ACTION_START)
        assertEquals("com.agentworkspace.runtime.PAUSE", AgentRunService.ACTION_PAUSE)
        assertEquals("com.agentworkspace.runtime.RESUME", AgentRunService.ACTION_RESUME)
        assertEquals("com.agentworkspace.runtime.CANCEL", AgentRunService.ACTION_CANCEL)
        assertEquals("run_id", AgentRunService.EXTRA_RUN_ID)
    }
}
