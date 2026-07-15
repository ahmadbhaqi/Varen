package com.agentworkspace.runtime.data

import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.readiness.domain.WorkspaceCapability
import com.agentworkspace.readiness.domain.WorkspaceKind
import com.agentworkspace.runtime.domain.RunConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunConfigurationCodecTest {
    @Test
    fun `round trip preserves the immutable capability snapshot`() {
        val configuration = RunConfiguration(
            connectionId = "connection",
            providerModelId = "model",
            workspaceId = "github://owner/repo?branch=main",
            trustMode = TrustMode.GUIDED,
            transport = "OPENAI_COMPATIBLE",
            workspaceKind = WorkspaceKind.GITHUB,
            capabilities = setOf(
                WorkspaceCapability.READ_FILES,
                WorkspaceCapability.WRITE_FILES,
                WorkspaceCapability.REMOTE_VERIFICATION,
            ),
            limitations = listOf("LIMIT_ONE"),
        )

        val decoded = RunConfigurationCodec.decode(RunConfigurationCodec.encode(configuration))

        assertEquals(configuration, decoded)
    }

    @Test
    fun `legacy local configuration decodes to a safe profile without command execution`() {
        val decoded = RunConfigurationCodec.decode(
            """{
                "connectionId":"connection",
                "providerModelId":"model",
                "workspaceId":"content://workspace",
                "trustMode":"GUIDED",
                "transport":"OPENAI_COMPATIBLE"
            }""".trimIndent(),
        )

        assertEquals(WorkspaceKind.LOCAL_SAF, decoded.workspaceKind)
        assertTrue(WorkspaceCapability.READ_FILES in decoded.capabilities)
        assertFalse(WorkspaceCapability.RUN_COMMAND in decoded.capabilities)
        assertEquals(listOf("COMMAND_EXECUTION_UNAVAILABLE"), decoded.limitations)
    }
}
