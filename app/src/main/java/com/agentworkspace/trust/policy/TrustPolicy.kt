package com.agentworkspace.trust.policy

import com.agentworkspace.data.model.TrustMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trust policy engine.
 *
 * Determines whether an action requires user approval based on the trust mode.
 *
 * Manual: Most meaningful actions require approval.
 * Guided: Agent continues freely, pauses for important steps.
 * Fully Trust: Proceeds without approval, but guardrails remain for destructive actions.
 */
@Singleton
class TrustPolicy @Inject constructor() {

    /**
     * Check if an action requires approval under the given trust mode.
     */
    fun requiresApproval(
        trustMode: TrustMode,
        action: AgentAction,
    ): ApprovalDecision {
        return when (trustMode) {
            TrustMode.MANUAL -> checkManual(action)
            TrustMode.GUIDED -> checkGuided(action)
            TrustMode.FULLY_TRUST -> checkFullyTrust(action)
        }
    }

    private fun checkManual(action: AgentAction): ApprovalDecision = when (action) {
        is AgentAction.ReadFile -> ApprovalDecision.notRequired("Read operations are safe")
        is AgentAction.SearchFiles -> ApprovalDecision.notRequired("Search operations are safe")
        is AgentAction.WriteFile -> ApprovalDecision.required(
            "Manual mode: file write requires approval",
            isDestructive = false,
        )
        is AgentAction.DeleteFile -> ApprovalDecision.required(
            "Manual mode: file deletion requires approval",
            isDestructive = true,
        )
        is AgentAction.MultiFileChange -> ApprovalDecision.required(
            "Manual mode: multi-file changes require approval",
            isDestructive = false,
        )
        is AgentAction.ExecuteCommand -> ApprovalDecision.required(
            "Manual mode: command execution requires approval",
            isDestructive = false,
        )
        is AgentAction.ProjectWideRefactor -> ApprovalDecision.required(
            "Manual mode: project-wide refactor requires approval and checkpoint",
            isDestructive = true,
            requiresCheckpoint = true,
        )
        is AgentAction.CreateCheckpoint -> ApprovalDecision.notRequired("Checkpoint creation is safe")
        is AgentAction.Rollback -> ApprovalDecision.required(
            "Manual mode: rollback requires approval",
            isDestructive = true,
        )
    }

    private fun checkGuided(action: AgentAction): ApprovalDecision = when (action) {
        is AgentAction.ReadFile -> ApprovalDecision.notRequired("Read operations are safe")
        is AgentAction.SearchFiles -> ApprovalDecision.notRequired("Search operations are safe")
        is AgentAction.WriteFile -> ApprovalDecision.required(
            "Guided mode: file write requires approval",
            isDestructive = false,
        )
        is AgentAction.DeleteFile -> ApprovalDecision.required(
            "Guided mode: file deletion requires approval",
            isDestructive = true,
        )
        is AgentAction.MultiFileChange -> ApprovalDecision.required(
            "Guided mode: multi-file changes require approval",
            isDestructive = false,
            requiresCheckpoint = true,
        )
        is AgentAction.ExecuteCommand -> ApprovalDecision.required(
            "Guided mode: command execution requires approval",
            isDestructive = false,
        )
        is AgentAction.ProjectWideRefactor -> ApprovalDecision.required(
            "Guided mode: project-wide refactor requires approval and checkpoint",
            isDestructive = true,
            requiresCheckpoint = true,
        )
        is AgentAction.CreateCheckpoint -> ApprovalDecision.notRequired("Checkpoint creation is safe")
        is AgentAction.Rollback -> ApprovalDecision.required(
            "Guided mode: rollback requires approval",
            isDestructive = true,
        )
    }

    private fun checkFullyTrust(action: AgentAction): ApprovalDecision = when (action) {
        is AgentAction.ReadFile -> ApprovalDecision.notRequired("Read operations are safe")
        is AgentAction.SearchFiles -> ApprovalDecision.notRequired("Search operations are safe")
        is AgentAction.WriteFile -> ApprovalDecision.notRequired("Fully trust: file write allowed")
        is AgentAction.DeleteFile -> ApprovalDecision.required(
            "Fully trust: file deletion still requires approval (guardrail)",
            isDestructive = true,
        )
        is AgentAction.MultiFileChange -> ApprovalDecision.notRequired(
            "Fully trust: multi-file changes allowed, checkpoint auto-created",
            autoCheckpoint = true,
        )
        is AgentAction.ExecuteCommand -> ApprovalDecision.notRequired("Fully trust: command execution allowed")
        is AgentAction.ProjectWideRefactor -> ApprovalDecision.required(
            "Fully trust: project-wide refactor requires approval (guardrail)",
            isDestructive = true,
            requiresCheckpoint = true,
        )
        is AgentAction.CreateCheckpoint -> ApprovalDecision.notRequired("Checkpoint creation is safe")
        is AgentAction.Rollback -> ApprovalDecision.required(
            "Fully trust: rollback still requires approval (guardrail)",
            isDestructive = true,
        )
    }
}

/**
 * Actions the agent can take, categorized for trust evaluation.
 */
sealed class AgentAction {
    data class ReadFile(val path: String) : AgentAction()
    data class SearchFiles(val query: String) : AgentAction()
    data class WriteFile(val path: String) : AgentAction()
    data class DeleteFile(val path: String) : AgentAction()
    data class MultiFileChange(val paths: List<String>) : AgentAction()
    data class ExecuteCommand(val command: String) : AgentAction()
    data class ProjectWideRefactor(val scope: String) : AgentAction()
    data object CreateCheckpoint : AgentAction()
    data class Rollback(val checkpointId: String) : AgentAction()
}

data class ApprovalDecision(
    val requiresApproval: Boolean,
    val reason: String,
    val isDestructive: Boolean = false,
    val requiresCheckpoint: Boolean = false,
    val autoCheckpoint: Boolean = false,
) {
    companion object {
        fun notRequired(reason: String, autoCheckpoint: Boolean = false) =
            ApprovalDecision(false, reason, autoCheckpoint = autoCheckpoint)

        fun required(
            reason: String,
            isDestructive: Boolean = false,
            requiresCheckpoint: Boolean = false,
        ) = ApprovalDecision(true, reason, isDestructive, requiresCheckpoint)
    }
}
