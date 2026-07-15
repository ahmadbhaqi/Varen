package com.agentworkspace.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.agentworkspace.data.model.*

@Entity(tableName = "usage_records")
data class UsageRecordEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val projectId: String?,
    val modelId: String?,
    val connectionId: String?,
    val session: String?,
    val timestamp: Long,
    val requests: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val cachedTokens: Int,
    val reasoningTokens: Int,
    val toolCalls: Int,
    val executionCount: Int,
    val filesRead: Int,
    val filesModified: Int,
    val searchCount: Int,
    val diffCount: Int,
    val checkpointCount: Int,
    val rollbackCount: Int,
    val errors: Int,
    val retries: Int,
    val latencyMs: Long,
) {
    fun toDomain(): UsageRecord = UsageRecord(
        id = id,
        taskId = taskId,
        projectId = projectId,
        modelId = modelId,
        connectionId = connectionId,
        session = session,
        timestamp = timestamp,
        requests = requests,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cachedTokens = cachedTokens,
        reasoningTokens = reasoningTokens,
        toolCalls = toolCalls,
        executionCount = executionCount,
        filesRead = filesRead,
        filesModified = filesModified,
        searchCount = searchCount,
        diffCount = diffCount,
        checkpointCount = checkpointCount,
        rollbackCount = rollbackCount,
        errors = errors,
        retries = retries,
        latencyMs = latencyMs,
    )

    companion object {
        fun fromDomain(record: UsageRecord): UsageRecordEntity = UsageRecordEntity(
            id = record.id,
            taskId = record.taskId,
            projectId = record.projectId,
            modelId = record.modelId,
            connectionId = record.connectionId,
            session = record.session,
            timestamp = record.timestamp,
            requests = record.requests,
            inputTokens = record.inputTokens,
            outputTokens = record.outputTokens,
            cachedTokens = record.cachedTokens,
            reasoningTokens = record.reasoningTokens,
            toolCalls = record.toolCalls,
            executionCount = record.executionCount,
            filesRead = record.filesRead,
            filesModified = record.filesModified,
            searchCount = record.searchCount,
            diffCount = record.diffCount,
            checkpointCount = record.checkpointCount,
            rollbackCount = record.rollbackCount,
            errors = record.errors,
            retries = record.retries,
            latencyMs = record.latencyMs,
        )
    }
}

@Entity(
    tableName = "checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("projectId"), Index("taskId")],
)
data class CheckpointEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val taskId: String?,
    val createdAt: Long,
    val scope: CheckpointScope,
    val filesJson: String,
    val reason: String,
    val isTrusted: Boolean,
)

@Entity(
    tableName = "diff_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("projectId"), Index("taskId")],
)
data class DiffEntryEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val projectId: String,
    val filePath: String,
    val originalContent: String,
    val newContent: String,
    val diffPatch: String,
    val createdAt: Long,
    val status: DiffStatus,
    val accepted: Boolean?,
)

@Entity(
    tableName = "history_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("projectId"), Index("taskId")],
)
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val taskId: String?,
    val timestamp: Long,
    val type: HistoryType,
    val modelUsed: String?,
    val connectionUsed: String?,
    val filesTouched: List<String>,
    val toolCalled: String?,
    val description: String,
    val usageConsumed: String?,
    val approvalState: String?,
    val checkpointState: String?,
    val rollbackState: String?,
    val fallbackState: String?,
    val success: Boolean,
    val details: String?,
)
