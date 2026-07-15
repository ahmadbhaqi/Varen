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

object WorkspaceCapabilityProfiles {
    private val fileCapabilities = setOf(
        WorkspaceCapability.LIST_FILES,
        WorkspaceCapability.READ_FILES,
        WorkspaceCapability.SEARCH_FILES,
        WorkspaceCapability.WRITE_FILES,
        WorkspaceCapability.DELETE_FILES,
    )

    fun forKind(kind: WorkspaceKind): WorkspaceCapabilityProfile = when (kind) {
        WorkspaceKind.LOCAL_SAF -> WorkspaceCapabilityProfile(
            kind = kind,
            capabilities = fileCapabilities + setOf(
                WorkspaceCapability.CHECKPOINTS,
                WorkspaceCapability.DIFFS,
            ),
        )

        WorkspaceKind.GITHUB -> WorkspaceCapabilityProfile(
            kind = kind,
            capabilities = fileCapabilities + WorkspaceCapability.REMOTE_VERIFICATION,
        )
    }
}

object ToolCapabilityPolicy {
    private val requirements = mapOf(
        "list_files" to WorkspaceCapability.LIST_FILES,
        "read_file" to WorkspaceCapability.READ_FILES,
        "search_files" to WorkspaceCapability.SEARCH_FILES,
        "write_file" to WorkspaceCapability.WRITE_FILES,
        "edit_file" to WorkspaceCapability.WRITE_FILES,
        "delete_file" to WorkspaceCapability.DELETE_FILES,
        "run_command" to WorkspaceCapability.RUN_COMMAND,
    )

    fun isBuiltIn(toolName: String): Boolean = toolName in requirements

    fun supports(
        toolName: String,
        capabilities: Set<WorkspaceCapability>,
    ): Boolean = requirements[toolName]?.let(capabilities::contains) == true
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
