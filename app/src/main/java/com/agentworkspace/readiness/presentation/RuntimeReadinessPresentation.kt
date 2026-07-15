package com.agentworkspace.readiness.presentation

import com.agentworkspace.readiness.domain.ReadinessAction
import com.agentworkspace.readiness.domain.RuntimeReadiness
import com.agentworkspace.readiness.domain.WorkspaceCapability

enum class ReadinessCardTone { READY, LIMITED, ACTION_REQUIRED }

data class ReadinessCardModel(
    val title: String,
    val detail: String,
    val tone: ReadinessCardTone,
    val actionLabel: String? = null,
    val action: ReadinessAction? = null,
)

fun runtimeReadinessCard(readiness: RuntimeReadiness): ReadinessCardModel = when (readiness) {
    is RuntimeReadiness.Blocked -> ReadinessCardModel(
        title = blockerTitle(readiness.blocker.code),
        detail = readiness.blocker.message,
        tone = ReadinessCardTone.ACTION_REQUIRED,
        actionLabel = actionLabel(readiness.blocker.action),
        action = readiness.blocker.action,
    )

    is RuntimeReadiness.Ready -> {
        val limited = readiness.limitations.isNotEmpty()
        val detail = when {
            readiness.limitations.any { it.code == "COMMAND_EXECUTION_UNAVAILABLE" } ->
                "File editing, checkpoints, and diffs are available. Shell commands are unavailable."

            WorkspaceCapability.REMOTE_VERIFICATION in readiness.profile.capabilities ->
                "Repository editing is available. Remote verification runs after the agent finishes."

            else -> "The selected project, connection, and model are ready."
        }
        ReadinessCardModel(
            title = if (limited) "Ready with limits" else "Ready to work",
            detail = detail,
            tone = if (limited) ReadinessCardTone.LIMITED else ReadinessCardTone.READY,
        )
    }
}

private fun blockerTitle(code: String): String = when (code) {
    "PROJECT_REQUIRED" -> "Attach a project"
    "MODEL_REQUIRED", "MODEL_UNAVAILABLE" -> "Choose a model"
    "CONNECTION_REQUIRED", "RUNTIME_ADAPTER_UNAVAILABLE" -> "Configure a connection"
    "AUTHENTICATION_REQUIRED" -> "Reconnect your account"
    "WORKSPACE_ACCESS_REQUIRED" -> "Restore workspace access"
    else -> "Setup needed"
}

private fun actionLabel(action: ReadinessAction): String = when (action) {
    ReadinessAction.CREATE_PROJECT -> "Add project"
    ReadinessAction.OPEN_CONNECTIONS -> "Open connections"
    ReadinessAction.REAUTHENTICATE -> "Reconnect"
    ReadinessAction.SELECT_MODEL -> "Choose model"
    ReadinessAction.RECONNECT_WORKSPACE -> "Open project"
}
