package com.agentworkspace.data.db.entity.runtime

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.agentworkspace.data.db.entity.TaskEntity
import com.agentworkspace.runtime.domain.ApprovalStatus
import com.agentworkspace.runtime.domain.RunCommandKind
import com.agentworkspace.runtime.domain.RunCommandStatus
import com.agentworkspace.runtime.domain.RunEventKind
import com.agentworkspace.runtime.domain.RunStatus

@Entity(
    tableName = "agent_runs",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("taskId"),
        Index("projectId"),
        Index("status"),
        Index(value = ["projectId", "status"]),
    ],
)
data class AgentRunEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val projectId: String,
    val status: RunStatus,
    val connectionId: String,
    val providerModelId: String,
    val workspaceId: String,
    val configurationJson: String,
    val leaseOwnerId: String? = null,
    val leaseExpiresAt: Long? = null,
    val heartbeatAt: Long? = null,
    val revision: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
)

@Entity(
    tableName = "run_commands",
    foreignKeys = [
        ForeignKey(
            entity = AgentRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index(value = ["status", "createdAt"])],
)
data class RunCommandEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val kind: RunCommandKind,
    val status: RunCommandStatus,
    val payloadJson: String = "{}",
    val createdAt: Long,
    val claimedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
)

@Entity(
    tableName = "run_events",
    foreignKeys = [
        ForeignKey(
            entity = AgentRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["runId", "sequence"], unique = true)],
)
data class RunEventEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val sequence: Long,
    val kind: RunEventKind,
    val payloadJson: String = "{}",
    val createdAt: Long,
)

@Entity(
    tableName = "run_messages",
    foreignKeys = [
        ForeignKey(
            entity = AgentRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["runId", "sequence"], unique = true)],
)
data class RunMessageEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val sequence: Long,
    val role: String,
    val content: String,
    val toolCallId: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "approval_requests",
    foreignKeys = [
        ForeignKey(
            entity = AgentRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index(value = ["runId", "status"])],
)
data class ApprovalRequestEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val actionType: String,
    val label: String,
    val reason: String,
    val risk: String,
    val status: ApprovalStatus,
    val requestedAt: Long,
    val resolvedAt: Long? = null,
)

@Entity(
    tableName = "side_effects",
    foreignKeys = [
        ForeignKey(
            entity = AgentRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index(value = ["idempotencyKey"], unique = true)],
)
data class SideEffectEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val toolCallId: String? = null,
    val idempotencyKey: String,
    val kind: String,
    val target: String,
    val status: String,
    val resultJson: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)
