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
import com.agentworkspace.shell.presentation.homeActiveProject
import com.agentworkspace.shell.presentation.homeContextModelName
import com.agentworkspace.shell.presentation.homeContextTrustMode
import com.agentworkspace.shell.presentation.homeConversationTasks
import com.agentworkspace.shell.presentation.HomeModelOption
import com.agentworkspace.shell.presentation.homeModelOptions
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val usageRepository: UsageRepository,
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {

    fun createAndStartTask(projectId: String, goal: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId).first()
            val selectedModel = project?.preferredModelId?.let { connectionRepository.getModelById(it) }
            val modelId = selectedModel?.name
            val connectionId = selectedModel?.connectionId

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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Combine all live signals so the cockpit never shows stale defaults.
        // The home screen is the workspace instrument cluster: connection
        // health, active model, trust mode and usage must reflect real state.
        viewModelScope.launch {
            combine(
                projectRepository.getRecentProjects(10),
                taskRepository.getActiveTasks(),
                taskRepository.getLatestTerminalTask(),
                connectionRepository.getAllConnections(),
                usageRepository.getTotalUsage(),
            ) { projects, activeTasks, latestTerminalTask, connections, usage ->
                val tasks = homeConversationTasks(activeTasks, latestTerminalTask)
                val activeTaskProject = tasks
                    .firstOrNull()
                    ?.projectId
                    ?.let { projectId -> projectRepository.getProjectById(projectId).first() }
                val displayProject = homeActiveProject(
                    activeTask = tasks.firstOrNull(),
                    recentProjects = projects,
                    activeTaskProject = activeTaskProject,
                )
                val projectPreferredModelName = displayProject
                    ?.preferredModelId
                    ?.let { modelId -> connectionRepository.getModelById(modelId)?.name }

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
                    activeTaskProject = activeTaskProject,
                    recentProjects = projects,
                )
                val activeModelName = homeContextModelName(
                    activeTask = tasks.firstOrNull(),
                    projectPreferredModelName = projectPreferredModelName,
                    defaultModelName = activeModel?.name ?: "No model",
                )

                val sessionTokens = (usage?.totalInputTokens ?: 0) + (usage?.totalOutputTokens ?: 0)

                HomeUiState(
                    recentProjects = projects,
                    activeTasks = tasks,
                    activeTaskProject = activeTaskProject,
                    activeModelName = activeModelName,
                    connectionStatus = connectionStatus,
                    trustMode = trustMode,
                    sessionTokens = sessionTokens,
                    modelOptions = modelOptions,
                    selectedModelId = selectedModelId,
                    isLoading = false,
                )
            }.collect { state -> _uiState.value = state }
        }
    }
}
