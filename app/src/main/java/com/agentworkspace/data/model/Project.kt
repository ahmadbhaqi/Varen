package com.agentworkspace.data.model

import java.util.UUID

/**
 * A project is the central unit of work.
 * Each project maintains its own context, tasks, history, usage, trust, model, checkpoints.
 */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val trustMode: TrustMode = TrustMode.MANUAL,
    val preferredModelId: String? = null,
    val preferredConnectionId: String? = null,
    val isActive: Boolean = true,
    val description: String = "",
)

enum class TrustMode(val displayName: String, val description: String) {
    MANUAL("Manual Approval", "User approves most meaningful actions"),
    GUIDED("Guided", "Agent continues freely, pauses for important steps"),
    FULLY_TRUST("Fully Trust", "Agent proceeds without repeated approvals, guardrails remain"),
}
