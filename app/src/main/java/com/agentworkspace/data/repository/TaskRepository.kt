package com.agentworkspace.data.repository

import com.agentworkspace.data.db.dao.TaskDao
import com.agentworkspace.data.db.entity.TaskEntity
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

internal val ACTIVE_TASK_STATUSES = listOf(
    TaskStatus.QUEUED,
    TaskStatus.PLANNING,
    TaskStatus.READING_CONTEXT,
    TaskStatus.EDITING,
    TaskStatus.RUNNING_LOOP,
    TaskStatus.VERIFYING,
    TaskStatus.WAITING_APPROVAL,
    TaskStatus.EXECUTING,
    TaskStatus.RETRYING,
    TaskStatus.PAUSED,
)

interface TaskRepository {
    fun getTasksForProject(projectId: String): Flow<List<Task>>
    fun getTaskById(id: String): Flow<Task?>
    fun getActiveTasks(): Flow<List<Task>>
    fun getActiveTasksForProject(projectId: String): Flow<List<Task>>
    fun getLatestTerminalTask(): Flow<Task?>
    suspend fun createTask(projectId: String, title: String, goal: String, modelId: String?, connectionId: String?): Task
    suspend fun updateTask(task: Task)
    suspend fun updateTaskStatus(id: String, status: TaskStatus)
    suspend fun updateTaskOutput(id: String, summary: String)
    suspend fun deleteTask(id: String)
}

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
) : TaskRepository {

    override fun getTasksForProject(projectId: String): Flow<List<Task>> =
        taskDao.getTasksForProject(projectId).map { list -> list.map { it.toDomain() } }

    override fun getTaskById(id: String): Flow<Task?> =
        taskDao.getTaskById(id).map { it?.toDomain() }

    override fun getActiveTasks(): Flow<List<Task>> =
        taskDao.getActiveTasks(ACTIVE_TASK_STATUSES).map { list -> list.map { it.toDomain() } }

    override fun getActiveTasksForProject(projectId: String): Flow<List<Task>> =
        taskDao.getActiveTasksForProject(projectId, ACTIVE_TASK_STATUSES).map { list -> list.map { it.toDomain() } }

    override fun getLatestTerminalTask(): Flow<Task?> =
        taskDao.getLatestTerminalTask().map { it?.toDomain() }

    override suspend fun createTask(
        projectId: String,
        title: String,
        goal: String,
        modelId: String?,
        connectionId: String?,
    ): Task {
        val task = Task(
            projectId = projectId,
            title = title,
            goal = goal,
            modelId = modelId,
            connectionId = connectionId,
        )
        taskDao.upsertTask(TaskEntity.fromDomain(task))
        return task
    }

    override suspend fun updateTask(task: Task) {
        taskDao.upsertTask(TaskEntity.fromDomain(task.copy(updatedAt = System.currentTimeMillis())))
    }

    override suspend fun updateTaskStatus(id: String, status: TaskStatus) {
        taskDao.updateTaskStatus(id, status)
    }

    override suspend fun updateTaskOutput(id: String, summary: String) {
        taskDao.updateTaskOutput(id, summary)
    }

    override suspend fun deleteTask(id: String) {
        taskDao.deleteTaskById(id)
    }
}
