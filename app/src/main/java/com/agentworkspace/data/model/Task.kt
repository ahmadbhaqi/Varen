package com.agentworkspace.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A task is the main visible action unit.
 * The product must make tasks feel central.
 */
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val goal: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val modelId: String? = null,
    val connectionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val filesRead: List<String> = emptyList(),
    val filesChanged: List<String> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val usage: TaskUsage = TaskUsage(),
    val executionAttempts: Int = 0,
    val warnings: List<String> = emptyList(),
    val approvals: List<ApprovalRecord> = emptyList(),
    val checkpoints: List<String> = emptyList(),
    val outputSummary: String = "",
    val agentPlan: List<AgentStep> = emptyList(),
    val currentStepIndex: Int = 0,
)

enum class TaskStatus(val displayName: String) {
    QUEUED("Queued"),
    PLANNING("Planning"),
    READING_CONTEXT("Reading Context"),
    EDITING("Editing"),
    RUNNING_LOOP("Running"),
    VERIFYING("Verifying"),
    WAITING_APPROVAL("Waiting Approval"),
    EXECUTING("Executing"),
    RETRYING("Retrying"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    ROLLED_BACK("Rolled Back"),
    PAUSED("Paused"),
}

@Serializable
data class ToolCall(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val input: String,
    val output: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true,
)

@Serializable
data class ApprovalRecord(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val approved: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String? = null,
)

@Serializable
data class TaskUsage(
    val requests: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val toolCalls: Int = 0,
    val executionCount: Int = 0,
    val filesRead: Int = 0,
    val filesModified: Int = 0,
    val searchCount: Int = 0,
    val diffCount: Int = 0,
    val checkpointCount: Int = 0,
    val rollbackCount: Int = 0,
    val errors: Int = 0,
    val retries: Int = 0,
    val latencyMs: Long = 0,
)

@Serializable
data class AgentStep(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val status: StepStatus = StepStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class StepStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
}
