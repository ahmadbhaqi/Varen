package com.agentworkspace.runtime.application

import com.agentworkspace.data.model.Connection
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
import com.agentworkspace.readiness.application.RuntimeReadinessCoordinator
import com.agentworkspace.readiness.domain.ReadinessBlocker
import com.agentworkspace.readiness.domain.RuntimeReadiness
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface RunStartDataSource {
    suspend fun getProject(projectId: String): Project?
    suspend fun getConnections(): List<Connection>
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

    override suspend fun getConnections(): List<Connection> =
        connectionRepository.getAllConnections().first()

    override suspend fun getTask(taskId: String): Task =
        taskRepository.getTaskById(taskId).first() ?: error("Task not found")

    override suspend fun saveTask(task: Task) {
        taskRepository.updateTask(task)
    }
}

sealed interface StartAgentRunResult {
    data class Started(val runId: String) : StartAgentRunResult
    data class Blocked(val blocker: ReadinessBlocker) : StartAgentRunResult
}

class StartAgentRun @Inject constructor(
    private val dataSource: RunStartDataSource,
    private val runCreator: RunCreator,
    private val launcher: RunLauncher,
    private val readinessCoordinator: RuntimeReadinessCoordinator,
) {
    suspend fun execute(
        projectId: String,
        taskId: String,
        goal: String,
        title: String,
    ): StartAgentRunResult {
        val project = dataSource.getProject(projectId) ?: error("Project not found")
        val connections = dataSource.getConnections()
        val readiness = readinessCoordinator.evaluate(project, connections)
        if (readiness is RuntimeReadiness.Blocked) {
            return StartAgentRunResult.Blocked(readiness.blocker)
        }
        readiness as RuntimeReadiness.Ready
        val modelApplicationId = project.preferredModelId
            ?: error("No model selected. Pick a model in project settings.")
        val connectionId = project.preferredConnectionId
            ?: error("Readiness allowed a project without an explicit connection")
        val connection = connections.firstOrNull { it.id == connectionId }
            ?: error("Readiness allowed an unavailable connection")
        val model = connection.models.firstOrNull { it.id == modelApplicationId }
            ?: error("Readiness allowed an unavailable model")

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
                    workspaceKind = readiness.profile.kind,
                    capabilities = readiness.profile.capabilities,
                    limitations = readiness.limitations.map { it.code },
                ),
            ),
        )
        launcher.start(run.id)
        return StartAgentRunResult.Started(run.id)
    }
}
