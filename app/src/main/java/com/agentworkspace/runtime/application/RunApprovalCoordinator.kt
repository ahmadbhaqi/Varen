package com.agentworkspace.runtime.application

import com.agentworkspace.runtime.data.ApprovalStore
import com.agentworkspace.runtime.domain.ApprovalStatus
import com.agentworkspace.runtime.service.RunLauncher
import com.agentworkspace.trust.policy.AgentAction
import com.agentworkspace.trust.policy.ApprovalDecision
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ApprovalRequester {
    suspend fun requestAndAwait(
        runId: String,
        action: AgentAction,
        decision: ApprovalDecision,
    ): Boolean
}

class RunApprovalCoordinator @Inject constructor(
    private val store: ApprovalStore,
) : ApprovalRequester {
    override suspend fun requestAndAwait(
        runId: String,
        action: AgentAction,
        decision: ApprovalDecision,
    ): Boolean {
        val approval = store.requestApproval(
            runId = runId,
            actionType = actionType(action),
            label = actionLabel(action),
            reason = decision.reason,
            risk = if (decision.isDestructive) "destructive" else "standard",
        )
        return store.observeApproval(approval.id)
            .filterNotNull()
            .map { it.status }
            .first { it != ApprovalStatus.PENDING } == ApprovalStatus.APPROVED
    }

    private fun actionType(action: AgentAction): String = when (action) {
        is AgentAction.ReadFile -> "read_file"
        is AgentAction.SearchFiles -> "search_files"
        is AgentAction.WriteFile -> "write_file"
        is AgentAction.DeleteFile -> "delete_file"
        is AgentAction.MultiFileChange -> "multi_file_change"
        is AgentAction.ExecuteCommand -> "execute_command"
        is AgentAction.ProjectWideRefactor -> "project_wide_refactor"
        AgentAction.CreateCheckpoint -> "create_checkpoint"
        is AgentAction.Rollback -> "rollback"
    }

    private fun actionLabel(action: AgentAction): String = when (action) {
        is AgentAction.ReadFile -> "read ${action.path}"
        is AgentAction.SearchFiles -> "search ${action.query}"
        is AgentAction.WriteFile -> "write to ${action.path}"
        is AgentAction.DeleteFile -> "delete ${action.path}"
        is AgentAction.MultiFileChange -> "change ${action.paths.size} files"
        is AgentAction.ExecuteCommand -> "run command: ${action.command}"
        is AgentAction.ProjectWideRefactor -> "project-wide refactor"
        AgentAction.CreateCheckpoint -> "create checkpoint"
        is AgentAction.Rollback -> "rollback"
    }
}

class ResolveRunApproval @Inject constructor(
    private val store: ApprovalStore,
    private val launcher: RunLauncher,
) {
    suspend fun execute(approvalId: String, approved: Boolean) {
        val approval = store.resolveApproval(approvalId, approved)
        launcher.resume(approval.runId)
    }
}
