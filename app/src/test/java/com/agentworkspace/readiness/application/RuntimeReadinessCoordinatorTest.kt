package com.agentworkspace.readiness.application

import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.AvailabilityState
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.ProviderType
import com.agentworkspace.readiness.domain.ReadinessAction
import com.agentworkspace.readiness.domain.RuntimeReadiness
import com.agentworkspace.readiness.domain.WorkspaceAccessChecker
import com.agentworkspace.readiness.domain.WorkspaceCapability
import com.agentworkspace.readiness.domain.WorkspaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeReadinessCoordinatorTest {
    @Test
    fun `missing project is the first blocker`() {
        assertBlocked(
            readiness = coordinator().evaluate(null, emptyList()),
            code = "PROJECT_REQUIRED",
            action = ReadinessAction.CREATE_PROJECT,
        )
    }

    @Test
    fun `missing preferred model blocks before a missing connection`() {
        val result = coordinator().evaluate(
            project(preferredModelId = null, preferredConnectionId = null),
            emptyList(),
        )

        assertBlocked(result, "MODEL_REQUIRED", ReadinessAction.SELECT_MODEL)
    }

    @Test
    fun `missing connection blocks after a preferred model is selected`() {
        val result = coordinator().evaluate(project(), emptyList())

        assertBlocked(result, "CONNECTION_REQUIRED", ReadinessAction.OPEN_CONNECTIONS)
    }

    @Test
    fun `missing preferred connection does not fall back by model`() {
        val result = coordinator().evaluate(
            project(preferredConnectionId = null),
            listOf(connection()),
        )

        assertBlocked(result, "CONNECTION_REQUIRED", ReadinessAction.OPEN_CONNECTIONS)
    }

    @Test
    fun `blank preferred connection does not fall back by model`() {
        val result = coordinator().evaluate(
            project(preferredConnectionId = " "),
            listOf(connection()),
        )

        assertBlocked(result, "CONNECTION_REQUIRED", ReadinessAction.OPEN_CONNECTIONS)
    }

    @Test
    fun `unauthenticated connection blocks before workspace limitations`() {
        val result = coordinator().evaluate(
            project(),
            listOf(connection(authState = AuthState.EXPIRED)),
        )

        assertBlocked(result, "AUTHENTICATION_REQUIRED", ReadinessAction.REAUTHENTICATE)
    }

    @Test
    fun `unsupported direct runtime blocks before model availability`() {
        val result = coordinator().evaluate(
            project(),
            listOf(
                connection(
                    presetId = "openai-codex",
                    availabilityState = AvailabilityState.UNAVAILABLE,
                ),
            ),
        )

        assertBlocked(result, "RUNTIME_ADAPTER_UNAVAILABLE", ReadinessAction.OPEN_CONNECTIONS)
    }

    @Test
    fun `missing preset has no explicit direct runtime adapter`() {
        val result = coordinator().evaluate(
            project(),
            listOf(connection(presetId = null)),
        )

        assertBlocked(result, "RUNTIME_ADAPTER_UNAVAILABLE", ReadinessAction.OPEN_CONNECTIONS)
    }

    @Test
    fun `unknown preset has no explicit direct runtime adapter`() {
        val result = coordinator().evaluate(
            project(),
            listOf(connection(presetId = "unknown-preset")),
        )

        assertBlocked(result, "RUNTIME_ADAPTER_UNAVAILABLE", ReadinessAction.OPEN_CONNECTIONS)
    }

    @Test
    fun `unavailable preferred model blocks before workspace access`() {
        val result = coordinator(canAccess = false).evaluate(
            project(),
            listOf(connection(availabilityState = AvailabilityState.UNAVAILABLE)),
        )

        assertBlocked(result, "MODEL_UNAVAILABLE", ReadinessAction.SELECT_MODEL)
    }

    @Test
    fun `missing workspace access is the final blocker`() {
        val result = coordinator(canAccess = false).evaluate(project(), listOf(connection()))

        assertBlocked(result, "WORKSPACE_ACCESS_REQUIRED", ReadinessAction.RECONNECT_WORKSPACE)
    }

    @Test
    fun `local SAF is ready with an explicit command limitation`() {
        val ready = coordinator().evaluate(
            project(path = "content://workspace"),
            listOf(connection()),
        ) as RuntimeReadiness.Ready

        assertEquals(WorkspaceKind.LOCAL_SAF, ready.profile.kind)
        assertEquals(
            setOf(
                WorkspaceCapability.LIST_FILES,
                WorkspaceCapability.READ_FILES,
                WorkspaceCapability.SEARCH_FILES,
                WorkspaceCapability.WRITE_FILES,
                WorkspaceCapability.DELETE_FILES,
                WorkspaceCapability.CHECKPOINTS,
                WorkspaceCapability.DIFFS,
            ),
            ready.profile.capabilities,
        )
        assertFalse(WorkspaceCapability.RUN_COMMAND in ready.profile.capabilities)
        assertTrue(ready.limitations.any { it.code == "COMMAND_EXECUTION_UNAVAILABLE" })
    }

    @Test
    fun `github exposes repository tools and its implemented remote verification`() {
        val ready = coordinator().evaluate(
            project(path = "github://owner/repo?branch=main"),
            listOf(connection()),
        ) as RuntimeReadiness.Ready

        assertEquals(WorkspaceKind.GITHUB, ready.profile.kind)
        assertEquals(
            setOf(
                WorkspaceCapability.LIST_FILES,
                WorkspaceCapability.READ_FILES,
                WorkspaceCapability.SEARCH_FILES,
                WorkspaceCapability.WRITE_FILES,
                WorkspaceCapability.DELETE_FILES,
                WorkspaceCapability.REMOTE_VERIFICATION,
            ),
            ready.profile.capabilities,
        )
        assertFalse(WorkspaceCapability.RUN_COMMAND in ready.profile.capabilities)
        assertTrue(WorkspaceCapability.REMOTE_VERIFICATION in ready.profile.capabilities)
    }

    private fun coordinator(canAccess: Boolean = true) = RuntimeReadinessCoordinator(
        workspaceAccessChecker = WorkspaceAccessChecker { canAccess },
        capabilityResolver = WorkspaceCapabilityResolver(),
    )

    private fun project(
        path: String = "content://workspace",
        preferredModelId: String? = MODEL_ID,
        preferredConnectionId: String? = CONNECTION_ID,
    ) = Project(
        id = "project",
        name = "Demo",
        path = path,
        preferredModelId = preferredModelId,
        preferredConnectionId = preferredConnectionId,
    )

    private fun connection(
        authState: AuthState = AuthState.AUTHENTICATED,
        availabilityState: AvailabilityState = AvailabilityState.AVAILABLE,
        presetId: String? = "openai",
    ) = Connection(
        id = CONNECTION_ID,
        name = "OpenAI",
        providerType = ProviderType.OPENAI,
        isEnabled = true,
        isHealthy = true,
        authState = authState,
        presetId = presetId,
        models = listOf(
            ModelInfo(
                id = MODEL_ID,
                name = "gpt-5",
                connectionId = CONNECTION_ID,
                availabilityState = availabilityState,
            ),
        ),
    )

    private fun RuntimeReadiness.blocker() = (this as RuntimeReadiness.Blocked).blocker

    private fun assertBlocked(
        readiness: RuntimeReadiness,
        code: String,
        action: ReadinessAction,
    ) {
        val blocker = readiness.blocker()
        assertEquals(code, blocker.code)
        assertEquals(action, blocker.action)
    }

    private companion object {
        const val CONNECTION_ID = "connection"
        const val MODEL_ID = "model"
    }
}
