package com.agentworkspace.runtime.domain

import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.readiness.domain.WorkspaceCapability
import com.agentworkspace.readiness.domain.WorkspaceKind

enum class RunStatus {
    QUEUED,
    PLANNING,
    READING_CONTEXT,
    CALLING_MODEL,
    WAITING_APPROVAL,
    EXECUTING_TOOL,
    EDITING,
    VERIFYING,
    RETRYING,
    RECOVERING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    ROLLED_BACK;

    val isTerminal: Boolean
        get() = this in TERMINAL_STATUSES

    private companion object {
        val TERMINAL_STATUSES = setOf(COMPLETED, FAILED, CANCELLED, ROLLED_BACK)
    }
}

enum class RunCommandKind {
    START,
    PAUSE,
    RESUME,
    CANCEL,
    APPROVE,
    DENY,
    RECOVER,
}

enum class RunCommandStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}

enum class RunEventKind {
    RUN_CREATED,
    STATUS_CHANGED,
    MESSAGE,
    ITERATION,
    TOOL_STARTED,
    TOOL_FINISHED,
    APPROVAL_REQUESTED,
    APPROVAL_RESOLVED,
    LEASE_ACQUIRED,
    LEASE_RELEASED,
    RECOVERY_STARTED,
    PAUSED,
    CANCELLED,
    COMPLETED,
    FAILED,
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
}

data class RunConfiguration(
    val connectionId: String,
    val providerModelId: String,
    val workspaceId: String,
    val trustMode: TrustMode,
    val transport: String,
    val systemPromptVersion: String = "agent-workspace-v1",
    val workspaceKind: WorkspaceKind,
    val capabilities: Set<WorkspaceCapability>,
    val limitations: List<String> = emptyList(),
)
