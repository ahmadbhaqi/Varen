package com.agentworkspace.readiness.domain

import com.agentworkspace.data.model.Project

enum class WorkspaceKind { LOCAL_SAF, GITHUB }

enum class WorkspaceCapability {
    LIST_FILES,
    READ_FILES,
    SEARCH_FILES,
    WRITE_FILES,
    DELETE_FILES,
    RUN_COMMAND,
    REMOTE_VERIFICATION,
    CHECKPOINTS,
    DIFFS,
}

data class WorkspaceCapabilityProfile(
    val kind: WorkspaceKind,
    val capabilities: Set<WorkspaceCapability>,
)

data class RuntimeLimitation(val code: String, val message: String)

enum class ReadinessAction {
    CREATE_PROJECT,
    OPEN_CONNECTIONS,
    REAUTHENTICATE,
    SELECT_MODEL,
    RECONNECT_WORKSPACE,
}

data class ReadinessBlocker(
    val code: String,
    val message: String,
    val action: ReadinessAction,
)

sealed interface RuntimeReadiness {
    data class Blocked(val blocker: ReadinessBlocker) : RuntimeReadiness

    data class Ready(
        val profile: WorkspaceCapabilityProfile,
        val limitations: List<RuntimeLimitation> = emptyList(),
    ) : RuntimeReadiness
}

fun interface WorkspaceAccessChecker {
    fun canAccess(project: Project): Boolean
}
