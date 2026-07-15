package com.agentworkspace.data.db.dao

import androidx.room.*
import com.agentworkspace.data.db.entity.TaskEntity
import com.agentworkspace.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getTasksForProject(projectId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskByIdOnce(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE status IN (:statuses) ORDER BY updatedAt DESC")
    fun getActiveTasks(statuses: List<TaskStatus>): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND status IN (:statuses) ORDER BY updatedAt DESC")
    fun getActiveTasksForProject(projectId: String, statuses: List<TaskStatus>): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status IN ('COMPLETED','FAILED') ORDER BY updatedAt DESC LIMIT 1")
    fun getLatestTerminalTask(): Flow<TaskEntity?>

    @Upsert
    suspend fun upsertTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("UPDATE tasks SET status = :status, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateTaskStatus(id: String, status: TaskStatus, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET outputSummary = :summary, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateTaskOutput(id: String, summary: String, timestamp: Long = System.currentTimeMillis())
}
