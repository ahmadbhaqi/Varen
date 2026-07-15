package com.agentworkspace.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.agentworkspace.data.model.*

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("projectId")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val goal: String,
    val status: TaskStatus,
    val modelId: String?,
    val connectionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val filesRead: List<String>,
    val filesChanged: List<String>,
    val toolCalls: List<ToolCall>,
    val usage: TaskUsage,
    val executionAttempts: Int,
    val warnings: List<String>,
    val approvals: List<ApprovalRecord>,
    val checkpoints: List<String>,
    val outputSummary: String,
    val agentPlan: List<AgentStep>,
    val currentStepIndex: Int,
) {
    fun toDomain(): Task = Task(
        id = id,
        projectId = projectId,
        title = title,
        goal = goal,
        status = status,
        modelId = modelId,
        connectionId = connectionId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        filesRead = filesRead,
        filesChanged = filesChanged,
        toolCalls = toolCalls,
        usage = usage,
        executionAttempts = executionAttempts,
        warnings = warnings,
        approvals = approvals,
        checkpoints = checkpoints,
        outputSummary = outputSummary,
        agentPlan = agentPlan,
        currentStepIndex = currentStepIndex,
    )

    companion object {
        fun fromDomain(task: Task): TaskEntity = TaskEntity(
            id = task.id,
            projectId = task.projectId,
            title = task.title,
            goal = task.goal,
            status = task.status,
            modelId = task.modelId,
            connectionId = task.connectionId,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            filesRead = task.filesRead,
            filesChanged = task.filesChanged,
            toolCalls = task.toolCalls,
            usage = task.usage,
            executionAttempts = task.executionAttempts,
            warnings = task.warnings,
            approvals = task.approvals,
            checkpoints = task.checkpoints,
            outputSummary = task.outputSummary,
            agentPlan = task.agentPlan,
            currentStepIndex = task.currentStepIndex,
        )
    }
}
