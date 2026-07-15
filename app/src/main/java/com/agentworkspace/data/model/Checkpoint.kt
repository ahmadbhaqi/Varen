package com.agentworkspace.data.model

import java.util.UUID

/**
 * Checkpoints protect user trust.
 * Before major edits or multi-file changes, the app creates checkpoints.
 */
data class Checkpoint(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val taskId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val scope: CheckpointScope = CheckpointScope.SINGLE_FILE,
    val files: List<CheckpointFile> = emptyList(),
    val reason: String,
    val isTrusted: Boolean = false,
)

enum class CheckpointScope(val displayName: String) {
    SINGLE_FILE("Single File"),
    MULTI_FILE("Multi-file"),
    PROJECT_WIDE("Project-wide"),
}

data class CheckpointFile(
    val path: String,
    val content: String,
    val hash: String,
)

/**
 * Diff is not optional.
 * The app must present AI changes through diffs.
 */
data class DiffEntry(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val projectId: String,
    val filePath: String,
    val originalContent: String,
    val newContent: String,
    val diffPatch: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: DiffStatus = DiffStatus.PENDING,
    val accepted: Boolean? = null,
)

enum class DiffStatus(val displayName: String) {
    PENDING("Pending Review"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    EDITED("Manually Edited"),
    APPLIED("Applied"),
}

/**
 * History is a first-class concept.
 * Structured, searchable activity timeline.
 */
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String? = null,
    val taskId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val type: HistoryType,
    val modelUsed: String? = null,
    val connectionUsed: String? = null,
    val filesTouched: List<String> = emptyList(),
    val toolCalled: String? = null,
    val description: String,
    val usageConsumed: String? = null,
    val approvalState: String? = null,
    val checkpointState: String? = null,
    val rollbackState: String? = null,
    val fallbackState: String? = null,
    val success: Boolean = true,
    val details: String? = null,
)

enum class HistoryType(val displayName: String) {
    TASK_STARTED("Task Started"),
    TASK_COMPLETED("Task Completed"),
    TASK_FAILED("Task Failed"),
    PLANNING("Planning"),
    FILE_READ("File Read"),
    FILE_WRITE("File Write"),
    TOOL_CALL("Tool Call"),
    COMMAND_EXECUTION("Command Execution"),
    APPROVAL_REQUESTED("Approval Requested"),
    APPROVAL_GRANTED("Approval Granted"),
    APPROVAL_DENIED("Approval Denied"),
    CHECKPOINT_CREATED("Checkpoint Created"),
    ROLLBACK("Rollback"),
    MODEL_SWITCHED("Model Switched"),
    FALLBACK_TRIGGERED("Fallback Triggered"),
    CONNECTION_EVENT("Connection Event"),
    ERROR("Error"),
    RETRY("Retry"),
}
