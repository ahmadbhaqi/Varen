package com.agentworkspace.readiness.application

import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.AvailabilityState
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.Project
import com.agentworkspace.readiness.domain.ReadinessAction
import com.agentworkspace.readiness.domain.ReadinessBlocker
import com.agentworkspace.readiness.domain.RuntimeLimitation
import com.agentworkspace.readiness.domain.RuntimeReadiness
import com.agentworkspace.readiness.domain.WorkspaceAccessChecker
import com.agentworkspace.readiness.domain.WorkspaceCapability
import com.agentworkspace.readiness.domain.WorkspaceCapabilityProfile
import com.agentworkspace.readiness.domain.WorkspaceKind
import javax.inject.Inject
import javax.inject.Singleton

class WorkspaceCapabilityResolver @Inject constructor() {
    fun resolve(project: Project): WorkspaceCapabilityProfile = when {
        project.path.startsWith("github://", ignoreCase = true) -> WorkspaceCapabilityProfile(
            kind = WorkspaceKind.GITHUB,
            capabilities = GITHUB_CAPABILITIES,
        )

        project.path.startsWith("content://", ignoreCase = true) -> WorkspaceCapabilityProfile(
            kind = WorkspaceKind.LOCAL_SAF,
            capabilities = LOCAL_SAF_CAPABILITIES,
        )

        else -> error("Unsupported workspace path: ${project.path}")
    }

    private companion object {
        val FILE_CAPABILITIES = setOf(
            WorkspaceCapability.LIST_FILES,
            WorkspaceCapability.READ_FILES,
            WorkspaceCapability.SEARCH_FILES,
            WorkspaceCapability.WRITE_FILES,
            WorkspaceCapability.DELETE_FILES,
        )
        val LOCAL_SAF_CAPABILITIES = FILE_CAPABILITIES + setOf(
            WorkspaceCapability.CHECKPOINTS,
            WorkspaceCapability.DIFFS,
        )
        val GITHUB_CAPABILITIES = FILE_CAPABILITIES + WorkspaceCapability.REMOTE_VERIFICATION
    }
}

@Singleton
class RuntimeReadinessCoordinator @Inject constructor(
    private val workspaceAccessChecker: WorkspaceAccessChecker,
    private val capabilityResolver: WorkspaceCapabilityResolver,
) {
    fun evaluate(project: Project?, connections: List<Connection>): RuntimeReadiness {
        val selectedProject = project ?: return blocked(
            code = "PROJECT_REQUIRED",
            message = "Create a project before starting agent work.",
            action = ReadinessAction.CREATE_PROJECT,
        )
        val preferredModelId = selectedProject.preferredModelId?.takeIf { it.isNotBlank() }
            ?: return blocked(
                code = "MODEL_REQUIRED",
                message = "Select a model for this project.",
                action = ReadinessAction.SELECT_MODEL,
            )
        val connection = selectedConnection(selectedProject, preferredModelId, connections)
            ?: return blocked(
                code = "CONNECTION_REQUIRED",
                message = "Choose an enabled connection for this project.",
                action = ReadinessAction.OPEN_CONNECTIONS,
            )
        if (connection.authState != AuthState.AUTHENTICATED) {
            return blocked(
                code = "AUTHENTICATION_REQUIRED",
                message = "Reauthenticate the selected connection.",
                action = ReadinessAction.REAUTHENTICATE,
            )
        }
        if (connection.preset?.supportsDirectRuntime == false) {
            return blocked(
                code = "RUNTIME_ADAPTER_UNAVAILABLE",
                message = "The selected connection has no direct Android runtime adapter.",
                action = ReadinessAction.OPEN_CONNECTIONS,
            )
        }
        val model = connection.models.firstOrNull { it.id == preferredModelId }
        if (model?.availabilityState != AvailabilityState.AVAILABLE) {
            return blocked(
                code = "MODEL_UNAVAILABLE",
                message = "The selected model is unavailable.",
                action = ReadinessAction.SELECT_MODEL,
            )
        }
        if (!workspaceAccessChecker.canAccess(selectedProject)) {
            return blocked(
                code = "WORKSPACE_ACCESS_REQUIRED",
                message = "Reconnect the workspace to restore read and write access.",
                action = ReadinessAction.RECONNECT_WORKSPACE,
            )
        }

        val profile = capabilityResolver.resolve(selectedProject)
        val limitations = if (profile.kind == WorkspaceKind.LOCAL_SAF) {
            listOf(
                RuntimeLimitation(
                    code = "COMMAND_EXECUTION_UNAVAILABLE",
                    message = "Android SAF workspaces do not provide local command execution.",
                ),
            )
        } else {
            emptyList()
        }
        return RuntimeReadiness.Ready(profile = profile, limitations = limitations)
    }

    private fun selectedConnection(
        project: Project,
        preferredModelId: String,
        connections: List<Connection>,
    ): Connection? {
        val preferredConnectionId = project.preferredConnectionId?.takeIf { it.isNotBlank() }
        return if (preferredConnectionId != null) {
            connections.firstOrNull { it.id == preferredConnectionId && it.isEnabled }
        } else {
            connections.firstOrNull { connection ->
                connection.isEnabled && connection.models.any { it.id == preferredModelId }
            }
        }
    }

    private fun blocked(
        code: String,
        message: String,
        action: ReadinessAction,
    ) = RuntimeReadiness.Blocked(
        ReadinessBlocker(code = code, message = message, action = action),
    )
}
