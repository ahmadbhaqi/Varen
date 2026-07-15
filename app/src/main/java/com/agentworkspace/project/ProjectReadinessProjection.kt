package com.agentworkspace.project

import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.Project
import com.agentworkspace.readiness.domain.ReadinessAction
import com.agentworkspace.readiness.domain.RuntimeReadiness
import com.agentworkspace.readiness.presentation.runtimeReadinessCard

data class ProjectReadinessSummary(
    val readiness: ReadinessState,
    val label: String,
    val activeModel: String,
    val activeConnection: String,
    val note: String,
)

fun projectReadinessSummary(
    project: Project?,
    connections: List<Connection>,
    runtimeReadiness: RuntimeReadiness,
): ProjectReadinessSummary {
    val selectedConnection = project?.preferredConnectionId?.takeIf(String::isNotBlank)
        ?.let { connectionId -> connections.firstOrNull { it.id == connectionId } }
    val selectedModel = project?.preferredModelId?.takeIf(String::isNotBlank)
        ?.let { modelId -> selectedConnection?.models?.firstOrNull { it.id == modelId } }
    val card = runtimeReadinessCard(runtimeReadiness)
    val state = when (runtimeReadiness) {
        is RuntimeReadiness.Ready -> ReadinessState.READY
        is RuntimeReadiness.Blocked -> when (runtimeReadiness.blocker.action) {
            ReadinessAction.SELECT_MODEL -> ReadinessState.NEEDS_MODEL
            ReadinessAction.REAUTHENTICATE -> ReadinessState.NEEDS_AUTH
            ReadinessAction.OPEN_CONNECTIONS -> if (
                runtimeReadiness.blocker.code == "RUNTIME_ADAPTER_UNAVAILABLE"
            ) {
                ReadinessState.REGISTERED_ONLY
            } else {
                ReadinessState.SETUP_REQUIRED
            }
            ReadinessAction.CREATE_PROJECT,
            ReadinessAction.RECONNECT_WORKSPACE,
            -> ReadinessState.SETUP_REQUIRED
        }
    }
    return ProjectReadinessSummary(
        readiness = state,
        label = card.title,
        activeModel = selectedModel?.name ?: "No model selected",
        activeConnection = selectedConnection?.name ?: "No connection",
        note = card.detail,
    )
}
