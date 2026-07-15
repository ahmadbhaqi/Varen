package com.agentworkspace.runtime.application

import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.data.repository.ConnectionRepository
import com.agentworkspace.data.repository.ProjectRepository
import com.agentworkspace.data.repository.TaskRepository
import com.agentworkspace.runtime.data.RunCreator
import com.agentworkspace.runtime.data.StartRunRequest
import com.agentworkspace.runtime.domain.RunConfiguration
import com.agentworkspace.runtime.service.RunLauncher
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface RunStartDataSource {
    suspend fun getProject(projectId: String): Project?
    suspend fun getModel(modelId: String): ModelInfo?
    suspend fun getConnection(connectionId: String): Connection?
    suspend fun getTask(taskId: String): Task
    suspend fun saveTask(task: Task)
}

@Singleton
class RepositoryRunStartDataSource @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val connectionRepository: ConnectionRepository,
) : RunStartDataSource {
    override suspend fun getProject(projectId: String): Project? =
        projectRepository.getProjectById(projectId).first()

    override suspend fun getModel(modelId: String): ModelInfo? =
        connectionRepository.getModelById(modelId)

    override suspend fun getConnection(connectionId: String): Connection? =
        connectionRepository.getConnectionById(connectionId).first()

    override suspend fun getTask(taskId: String): Task =
        taskRepository.getTaskById(taskId).first() ?: error("Task not found")

    override suspend fun saveTask(task: Task) {
        taskRepository.updateTask(task)
    }
}

class StartAgentRun @Inject constructor(
    private val dataSource: RunStartDataSource,
    private val runCreator: RunCreator,
    private val launcher: RunLauncher,
) {
    suspend fun execute(
        projectId: String,
        taskId: String,
        goal: String,
        title: String,
    ): String {
        val project = dataSource.getProject(projectId) ?: error("Project not found")
        val modelApplicationId = project.preferredModelId
            ?: error("No model selected. Pick a model in project settings.")
        val model = dataSource.getModel(modelApplicationId)
            ?: error("Selected model is unavailable")
        val connectionId = project.preferredConnectionId ?: model.connectionId
        val connection = dataSource.getConnection(connectionId)
            ?.takeIf { it.isEnabled }
            ?: error("Selected connection is unavailable")
        check(connection.id == model.connectionId) {
            "Selected model does not belong to the selected connection"
        }

        val currentTask = dataSource.getTask(taskId)
        val task = currentTask.copy(
            title = currentTask.title.takeUnless { it.isBlank() || it == "New task" }
                ?: title.ifBlank { "Agent task" },
            goal = goal,
            status = TaskStatus.QUEUED,
            modelId = model.name,
            connectionId = connection.id,
            outputSummary = "",
        )
        dataSource.saveTask(task)

        val run = runCreator.createRun(
            StartRunRequest(
                taskId = task.id,
                projectId = project.id,
                initialUserMessage = task.goal,
                configuration = RunConfiguration(
                    connectionId = connection.id,
                    providerModelId = model.name,
                    workspaceId = project.path,
                    trustMode = project.trustMode,
                    transport = connection.preset?.transportFormat?.name ?: "OPENAI_COMPATIBLE",
                ),
            ),
        )
        launcher.start(run.id)
        return run.id
    }
}
