package com.agentworkspace.project

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.DiffStatus
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.data.repository.CheckpointRepository
import com.agentworkspace.data.repository.ConnectionRepository
import com.agentworkspace.data.repository.DiffRepository
import com.agentworkspace.data.repository.HistoryRepository
import com.agentworkspace.data.repository.ProjectRepository
import com.agentworkspace.data.repository.TaskRepository
import com.agentworkspace.data.repository.UsageRepository
import com.agentworkspace.github.GitHubRemoteRepository
import com.agentworkspace.github.githubProjectPath
import com.agentworkspace.github.isGitHubProjectPath
import com.agentworkspace.github.parseGitHubProjectPath
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A browseable entry in the project workspace file tree. */
data class FileEntry(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val childCount: Int,
)

data class ProjectOverview(
    val readiness: ReadinessState = ReadinessState.SETUP_REQUIRED,
    val readinessLabel: String = "Setup required",
    val activeModel: String = "No model selected",
    val activeConnection: String = "No connection",
    val runtimeNote: String? = null,
    val activeTasks: Int = 0,
    val completedTasks: Int = 0,
    val pendingReview: Int = 0,
    val totalTokens: Int = 0,
    val totalRequests: Int = 0,
    val diffCount: Int = 0,
    val checkpointCount: Int = 0,
    val recentHistory: List<HistoryEntry> = emptyList(),
)

enum class ReadinessState {
    READY,
    NEEDS_MODEL,
    NEEDS_AUTH,
    REGISTERED_ONLY,
    SETUP_REQUIRED,
}

private data class ProjectActivitySignals(
    val tasks: List<com.agentworkspace.data.model.Task>,
    val usage: List<com.agentworkspace.data.model.UsageRecord>,
    val checkpoints: List<com.agentworkspace.data.model.Checkpoint>,
    val diffs: List<com.agentworkspace.data.model.DiffEntry>,
    val history: List<HistoryEntry>,
)

@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val usageRepository: UsageRepository,
    private val checkpointRepository: CheckpointRepository,
    private val diffRepository: DiffRepository,
    private val historyRepository: HistoryRepository,
    private val connectionRepository: ConnectionRepository,
    private val githubRemoteRepository: GitHubRemoteRepository,
) : ViewModel() {

    private val _createState = MutableStateFlow<CreateProjectState>(CreateProjectState.Idle)
    val createState = _createState.asStateFlow()

    fun getProject(projectId: String): StateFlow<Project?> =
        projectRepository.getProjectById(projectId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun getProjectTasks(projectId: String): StateFlow<List<com.agentworkspace.data.model.Task>> =
        taskRepository.getTasksForProject(projectId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getProjectOverview(projectId: String): StateFlow<ProjectOverview> {
        val activitySignals = combine(
            taskRepository.getTasksForProject(projectId),
            usageRepository.getUsageForProject(projectId),
            checkpointRepository.getCheckpointsForProject(projectId),
            diffRepository.getDiffsForProject(projectId),
            historyRepository.getHistoryForProject(projectId),
        ) { tasks, usage, checkpoints, diffs, history ->
            ProjectActivitySignals(tasks, usage, checkpoints, diffs, history)
        }

        return combine(
            projectRepository.getProjectById(projectId),
            activitySignals,
            connectionRepository.getAllConnections(),
        ) { project, signals, connections ->
            val enabled = connections.filter { it.isEnabled }
            val preferredConnection = project?.preferredConnectionId?.let { id ->
                enabled.firstOrNull { it.id == id }
            }
            val authenticated = enabled.filter { it.authState == AuthState.AUTHENTICATED }
            val connection = preferredConnection ?: authenticated.firstOrNull() ?: enabled.firstOrNull()
            val preferredModel = project?.preferredModelId
            val model = preferredModel?.let { id ->
                connections.flatMap { it.models }.firstOrNull { it.id == id }
            } ?: connection?.models?.firstOrNull { it.isRecommended }
                ?: connection?.models?.firstOrNull()

            val readiness = when {
                connection == null -> ReadinessState.SETUP_REQUIRED
                connection.authState != AuthState.AUTHENTICATED -> ReadinessState.NEEDS_AUTH
                model == null -> ReadinessState.NEEDS_MODEL
                connection.preset?.supportsDirectRuntime == false -> ReadinessState.REGISTERED_ONLY
                else -> ReadinessState.READY
            }
            val label = when (readiness) {
                ReadinessState.READY -> "Ready to run"
                ReadinessState.NEEDS_MODEL -> "Select a model"
                ReadinessState.NEEDS_AUTH -> "Reconnect provider"
                ReadinessState.REGISTERED_ONLY -> "Login registered"
                ReadinessState.SETUP_REQUIRED -> "Add connection"
            }
            val note = when (readiness) {
                ReadinessState.REGISTERED_ONLY -> "This provider is connected, but its native Android runtime adapter is not executable yet."
                ReadinessState.NEEDS_MODEL -> "Choose a project model so the agent never switches silently."
                ReadinessState.NEEDS_AUTH -> "The provider exists, but authentication is not ready for agent work."
                ReadinessState.SETUP_REQUIRED -> "Add a model connection before starting project work."
                ReadinessState.READY -> null
            }

            ProjectOverview(
                readiness = readiness,
                readinessLabel = label,
                activeModel = model?.name ?: "No model selected",
                activeConnection = connection?.name ?: "No connection",
                runtimeNote = note,
                activeTasks = signals.tasks.count { it.status in activeStatuses },
                completedTasks = signals.tasks.count { it.status == TaskStatus.COMPLETED },
                pendingReview = signals.tasks.count { it.status == TaskStatus.WAITING_APPROVAL } +
                    signals.diffs.count { it.status == DiffStatus.PENDING },
                totalTokens = signals.usage.sumOf { it.inputTokens + it.outputTokens },
                totalRequests = signals.usage.sumOf { it.requests },
                diffCount = signals.diffs.size,
                checkpointCount = signals.checkpoints.size,
                recentHistory = signals.history.take(4),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectOverview())
    }

    fun createProject(name: String, path: String, description: String, trustMode: TrustMode) {
        viewModelScope.launch {
            _createState.value = CreateProjectState.Loading
            try {
                val workspaceUri = Uri.parse(path)
                val safePath = validateWorkspaceFolder(workspaceUri)
                val safeName = name.trim().ifBlank { workspaceFolderName(workspaceUri) }
                val project = projectRepository.createProject(safeName, safePath, description.trim())
                projectRepository.updateTrustMode(project.id, trustMode)
                _createState.value = CreateProjectState.Success(project.id)
            } catch (e: Exception) {
                _createState.value = CreateProjectState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createProjectFromWorkspace(
        name: String,
        workspaceUri: Uri,
        description: String,
        trustMode: TrustMode,
    ) {
        createProject(name, workspaceUri.toString(), description, trustMode)
    }

    fun createGitHubRemoteProject(
        repo: String,
        branch: String,
        token: String,
        name: String,
        description: String,
        trustMode: TrustMode,
    ) {
        viewModelScope.launch {
            _createState.value = CreateProjectState.Loading
            try {
                val remote = githubRemoteRepository.configureProject(
                    repoInput = repo,
                    branchInput = branch,
                    tokenInput = token,
                )
                val project = projectRepository.createProject(
                    name = name.trim().ifBlank { remote.name },
                    path = githubProjectPath(remote.owner, remote.name, remote.baseBranch),
                    description = description.trim().ifBlank { "Remote GitHub project: ${remote.fullName}@${remote.baseBranch}" },
                )
                projectRepository.updateTrustMode(project.id, trustMode)
                _createState.value = CreateProjectState.Success(project.id)
            } catch (e: Exception) {
                _createState.value = CreateProjectState.Error(e.message ?: "Unknown GitHub error")
            }
        }
    }

    fun hasSavedGitHubToken(): Boolean = githubRemoteRepository.hasSavedToken()

    fun updateTrustMode(projectId: String, trustMode: TrustMode) {
        viewModelScope.launch {
            projectRepository.updateTrustMode(projectId, trustMode)
        }
    }

    fun updatePreferredModel(projectId: String, modelId: String?) {
        viewModelScope.launch {
            projectRepository.updatePreferredModel(projectId, modelId)
        }
    }

    /**
     * Create a draft task so the user can jump straight into the agent panel,
     * where the actual goal is entered. Returns the new task id via [onCreated].
     */
    fun createStarterTask(projectId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val task = taskRepository.createTask(
                projectId = projectId,
                title = "New task",
                goal = "",
                modelId = null,
                connectionId = null,
            )
            onCreated(task.id)
        }
    }

    /**
     * List the top-level files and folders of a project workspace so the
     * project screen can show a real context tree (a required section),
     * and so opening a file hands a real content:// URI to the editor.
     */
    fun listProjectFiles(projectId: String, subPath: String? = null): kotlinx.coroutines.flow.Flow<List<FileEntry>> = flow {
        val project = projectRepository.getProjectById(projectId).first() ?: run { emit(emptyList()); return@flow }
        if (isGitHubProjectPath(project.path)) {
            val remote = parseGitHubProjectPath(project.path)
            val entries = githubRemoteRepository.listFiles(
                project = remote,
                branch = remote.baseBranch,
                path = subPath.orEmpty(),
                recursive = false,
            ).map { file ->
                FileEntry(
                    name = file.name,
                    uri = file.path,
                    isDirectory = file.isDirectory,
                    childCount = file.childCount,
                )
            }
            emit(entries)
            return@flow
        }
        val treeUri = runCatching { Uri.parse(project.path) }.getOrNull() ?: run { emit(emptyList()); return@flow }
        val root = if (subPath.isNullOrBlank()) {
            DocumentFile.fromTreeUri(context, treeUri)
        } else {
            DocumentFile.fromTreeUri(context, treeUri)?.findFile(subPath)
        } ?: run { emit(emptyList()); return@flow }
        if (!root.isDirectory) { emit(emptyList()); return@flow }
        val entries = root.listFiles()
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name ?: "" })
            .map { doc ->
                FileEntry(
                    name = doc.name ?: "unknown",
                    uri = doc.uri.toString(),
                    isDirectory = doc.isDirectory,
                    childCount = if (doc.isDirectory) doc.listFiles().size else 0,
                )
            }
        emit(entries)
    }.flowOn(Dispatchers.IO)

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }

    fun resetCreateState() {
        _createState.value = CreateProjectState.Idle
    }

    private fun validateWorkspaceFolder(uri: Uri): String {
        require(uri.scheme == "content") {
            "Choose the project folder with the system folder picker."
        }
        persistWorkspacePermission(uri)
        val folder = DocumentFile.fromTreeUri(context, uri)
            ?: throw IllegalArgumentException("Selected folder is not available.")
        require(folder.isDirectory) { "Selected target is not a folder." }
        require(folder.canRead()) { "The app does not have read access to this folder." }
        require(folder.canWrite()) { "The app does not have write access to this folder." }
        return uri.toString()
    }

    private fun workspaceFolderName(uri: Uri): String =
        DocumentFile.fromTreeUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast(':')
            ?: "Untitled Project"

    private fun persistWorkspacePermission(uri: Uri) {
        val alreadyPersisted = context.contentResolver.persistedUriPermissions.any { grant ->
            grant.uri == uri && grant.isReadPermission && grant.isWritePermission
        }
        if (alreadyPersisted) return

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }

        val persistedAfterAttempt = context.contentResolver.persistedUriPermissions.any { grant ->
            grant.uri == uri && grant.isReadPermission && grant.isWritePermission
        }
        require(persistedAfterAttempt) {
            "Could not save folder access. Choose the folder again with the system picker."
        }
    }

    sealed class CreateProjectState {
        object Idle : CreateProjectState()
        object Loading : CreateProjectState()
        data class Success(val projectId: String) : CreateProjectState()
        data class Error(val message: String) : CreateProjectState()
    }

    val allProjects: StateFlow<List<com.agentworkspace.data.model.Project>> =
        projectRepository.getAllProjects()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private companion object {
        val activeStatuses = setOf(
            TaskStatus.QUEUED,
            TaskStatus.PLANNING,
            TaskStatus.READING_CONTEXT,
            TaskStatus.EDITING,
            TaskStatus.RUNNING_LOOP,
            TaskStatus.VERIFYING,
            TaskStatus.WAITING_APPROVAL,
            TaskStatus.EXECUTING,
            TaskStatus.RETRYING,
        )
    }
}
