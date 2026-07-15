package com.agentworkspace.workspace.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.data.repository.ConnectionRepository
import com.agentworkspace.data.repository.ProjectRepository
import com.agentworkspace.data.repository.TaskRepository
import com.agentworkspace.data.repository.UsageRepository
import com.agentworkspace.readiness.application.RuntimeReadinessCoordinator
import com.agentworkspace.readiness.domain.ReadinessBlocker
import com.agentworkspace.readiness.domain.RuntimeReadiness
import com.agentworkspace.readiness.presentation.ReadinessCardModel
import com.agentworkspace.readiness.presentation.runtimeReadinessCard
import com.agentworkspace.shell.presentation.homeActiveProject
import com.agentworkspace.shell.presentation.homeContextModelName
import com.agentworkspace.shell.presentation.homeContextTrustMode
import com.agentworkspace.shell.presentation.homeConversationTasks
import com.agentworkspace.shell.presentation.HomeModelOption
import com.agentworkspace.shell.presentation.homeModelOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentProjects: List<Project> = emptyList(),
    val activeTasks: List<Task> = emptyList(),
    val activeTaskProject: Project? = null,
    val activeModelName: String = "No model",
    val connectionStatus: String = "Disconnected",
    val trustMode: String = TrustMode.MANUAL.displayName,
    val sessionTokens: Int = 0,
    val modelOptions: List<HomeModelOption> = emptyList(),
    val selectedModelId: String? = null,
    val runtimeReadiness: RuntimeReadiness? = null,
    val readinessCard: ReadinessCardModel? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val usageRepository: UsageRepository,
    private val connectionRepository: ConnectionRepository,
    private val readinessCoordinator: RuntimeReadinessCoordinator,
) : ViewModel() {

    fun createAndStartTask(
        projectId: String,
        goal: String,
        onBlocked: (ReadinessBlocker) -> Unit,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId).first()
            val connections = connectionRepository.getAllConnections().first()
            val readiness = readinessCoordinator.evaluate(project, connections)
            if (readiness is RuntimeReadiness.Blocked) {
                onBlocked(readiness.blocker)
                return@launch
            }
            val selectedModel = project?.preferredModelId?.let { modelId ->
                connections.flatMap { it.models }.firstOrNull { it.id == modelId }
            }
            val modelId = selectedModel?.name
            val connectionId = project?.preferredConnectionId

            val task = taskRepository.createTask(
                projectId = projectId,
                title = goal.take(60).ifBlank { "Agent task" },
                goal = goal,
                modelId = modelId,
                connectionId = connectionId,
            )
            taskRepository.updateTaskStatus(task.id, TaskStatus.QUEUED)
            onCreated(task.id)
        }
    }

    fun useModelForProject(projectId: String, option: HomeModelOption) {
        if (!option.enabled) return
        viewModelScope.launch {
            projectRepository.updatePreferredModel(projectId, option.id)
            projectRepository.updatePreferredConnection(projectId, option.connectionId)
        }
    }

    fun reconnectWorkspace(projectId: String, workspacePath: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId).first() ?: return@launch
            projectRepository.updateProject(project.copy(path = workspacePath))
        }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val conversationTasks = combine(
        taskRepository.getActiveTasks(),
        taskRepository.getLatestTerminalTask(),
    ) { activeTasks, latestTerminalTask ->
        homeConversationTasks(activeTasks, latestTerminalTask)
    }.distinctUntilChanged()

    private val activeTaskProject = conversationTasks
        .map { tasks -> tasks.firstOrNull()?.projectId }
        .distinctUntilChanged()
        .flatMapLatest { projectId ->
            projectId?.let(projectRepository::getProjectById) ?: flowOf(null)
        }

    init {
        // Combine all live signals so the cockpit never shows stale defaults.
        // The home screen is the workspace instrument cluster: connection
        // health, active model, trust mode and usage must reflect real state.
        viewModelScope.launch {
            combine(
                projectRepository.getRecentProjects(10),
                conversationTasks,
                activeTaskProject,
                connectionRepository.getAllConnections(),
                usageRepository.getTotalUsage(),
            ) { projects, tasks, taskProject, connections, usage ->
                val displayProject = homeActiveProject(
                    activeTask = tasks.firstOrNull(),
                    recentProjects = projects,
                    activeTaskProject = taskProject,
                )
                val projectPreferredModelName = displayProject
                    ?.preferredModelId
                    ?.let { modelId -> connections.flatMap { it.models }.firstOrNull { it.id == modelId }?.name }

                val enabled = connections.filter { it.isEnabled }
                val authenticated = enabled.filter { it.authState == AuthState.AUTHENTICATED }
                val authenticatedConnectionIds = authenticated.map { it.id }.toSet()

                val connectionStatus = when {
                    authenticated.isNotEmpty() -> "Connected"
                    enabled.isNotEmpty() -> "Auth needed"
                    else -> "No connection"
                }

                val activeModel = authenticated
                    .flatMap { it.models }
                    .firstOrNull { it.isRecommended }
                    ?: authenticated.flatMap { it.models }.firstOrNull()
                val selectedModelId = displayProject?.preferredModelId
                val modelOptions = homeModelOptions(
                    models = enabled.flatMap { it.models },
                    selectedModelId = selectedModelId,
                    hasProject = displayProject != null,
                ).map { option ->
                    if (option.connectionId in authenticatedConnectionIds) {
                        option
                    } else {
                        option.copy(enabled = false, detail = "Authentication required")
                    }
                }

                val trustMode = homeContextTrustMode(
                    activeTask = tasks.firstOrNull(),
                    activeTaskProject = taskProject,
                    recentProjects = projects,
                )
                val activeModelName = homeContextModelName(
                    activeTask = tasks.firstOrNull(),
                    projectPreferredModelName = projectPreferredModelName,
                    defaultModelName = activeModel?.name ?: "No model",
                )

                val sessionTokens = (usage?.totalInputTokens ?: 0) + (usage?.totalOutputTokens ?: 0)
                val runtimeReadiness = readinessCoordinator.evaluate(displayProject, connections)

                HomeUiState(
                    recentProjects = projects,
                    activeTasks = tasks,
                    activeTaskProject = taskProject,
                    activeModelName = activeModelName,
                    connectionStatus = connectionStatus,
                    trustMode = trustMode,
                    sessionTokens = sessionTokens,
                    modelOptions = modelOptions,
                    selectedModelId = selectedModelId,
                    runtimeReadiness = runtimeReadiness,
                    readinessCard = runtimeReadinessCard(runtimeReadiness),
                    isLoading = false,
                )
            }.collect { state -> _uiState.value = state }
        }
    }
}
