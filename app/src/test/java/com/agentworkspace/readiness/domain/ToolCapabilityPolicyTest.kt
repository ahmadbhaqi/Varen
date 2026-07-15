package com.agentworkspace.readiness.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCapabilityPolicyTest {
    @Test
    fun `local SAF profile does not advertise or execute run command`() {
        val capabilities = setOf(
            WorkspaceCapability.LIST_FILES,
            WorkspaceCapability.READ_FILES,
            WorkspaceCapability.WRITE_FILES,
        )

        assertTrue(ToolCapabilityPolicy.supports("read_file", capabilities))
        assertTrue(ToolCapabilityPolicy.supports("edit_file", capabilities))
        assertFalse(ToolCapabilityPolicy.supports("run_command", capabilities))
    }

    @Test
    fun `unknown names are not treated as built in tools`() {
        assertFalse(ToolCapabilityPolicy.isBuiltIn("mcp__server__tool"))
    }
}
