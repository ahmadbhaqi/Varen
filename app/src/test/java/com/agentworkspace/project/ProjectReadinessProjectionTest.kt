package com.agentworkspace.project

import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.ProviderType
import com.agentworkspace.readiness.application.RuntimeReadinessCoordinator
import com.agentworkspace.readiness.application.WorkspaceCapabilityResolver
import com.agentworkspace.readiness.domain.WorkspaceAccessChecker
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectReadinessProjectionTest {
    @Test
    fun `project detail never falls back to first authenticated connection or model`() {
        val project = project(preferredConnectionId = null, preferredModelId = null)
        val connections = listOf(connection())
        val readiness = coordinator().evaluate(project, connections)

        val summary = projectReadinessSummary(project, connections, readiness)

        assertEquals(ReadinessState.NEEDS_MODEL, summary.readiness)
        assertEquals("Choose a model", summary.label)
        assertEquals("No connection", summary.activeConnection)
        assertEquals("No model selected", summary.activeModel)
    }

    @Test
    fun `ready local project reuses shared command limitation copy`() {
        val project = project()
        val connections = listOf(connection())
        val readiness = coordinator().evaluate(project, connections)

        val summary = projectReadinessSummary(project, connections, readiness)

        assertEquals(ReadinessState.READY, summary.readiness)
        assertEquals("Ready with limits", summary.label)
        assertEquals("Connection", summary.activeConnection)
        assertEquals("model", summary.activeModel)
        assertEquals(true, summary.note?.contains("Shell commands are unavailable"))
    }

    private fun coordinator() = RuntimeReadinessCoordinator(
        workspaceAccessChecker = WorkspaceAccessChecker { true },
        capabilityResolver = WorkspaceCapabilityResolver(),
    )

    private fun project(
        preferredConnectionId: String? = "connection",
        preferredModelId: String? = "model-id",
    ) = Project(
        id = "project",
        name = "Project",
        path = "content://workspace",
        preferredConnectionId = preferredConnectionId,
        preferredModelId = preferredModelId,
    )

    private fun connection() = Connection(
        id = "connection",
        name = "Connection",
        providerType = ProviderType.OPENAI,
        authState = AuthState.AUTHENTICATED,
        presetId = "openai",
        models = listOf(
            ModelInfo(
                id = "model-id",
                name = "model",
                connectionId = "connection",
                isRecommended = true,
            ),
        ),
    )
}
