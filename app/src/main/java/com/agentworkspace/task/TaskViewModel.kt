package com.agentworkspace.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.checkpoint.CheckpointManager
import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.db.entity.runtime.ApprovalRequestEntity
import com.agentworkspace.data.db.entity.runtime.RunEventEntity
import com.agentworkspace.data.model.Checkpoint
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.data.repository.TaskRepository
import com.agentworkspace.runtime.application.ResolveRunApproval
import com.agentworkspace.runtime.application.StartAgentRun
import com.agentworkspace.runtime.data.AgentRunRepository
import com.agentworkspace.runtime.service.RunLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Projects durable task/run state for the task screen and dispatches short-lived
 * user commands. The actual agent execution belongs to [com.agentworkspace.runtime.service.AgentRunService].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val checkpointManager: CheckpointManager,
    private val startAgentRun: StartAgentRun,
    private val runRepository: AgentRunRepository,
    private val resolveRunApproval: ResolveRunApproval,
    private val runLauncher: RunLauncher,
) : ViewModel() {
    private val boundTaskId = MutableStateFlow<String?>(null)
    private val commandLine = MutableStateFlow<ChatLine?>(null)
    private val startInFlight = MutableStateFlow(false)
    private val requestedRunId = MutableStateFlow<String?>(null)

    val task: StateFlow<Task?> = boundTaskId
        .flatMapLatest { taskId ->
            if (taskId == null) flowOf<Task?>(null) else taskRepository.getTaskById(taskId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val latestRun: StateFlow<AgentRunEntity?> = boundTaskId
        .flatMapLatest { taskId ->
            if (taskId == null) flowOf<AgentRunEntity?>(null)
            else runRepository.observeLatestRunForTask(taskId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val persistedEvents: StateFlow<List<RunEventEntity>> = latestRun
        .flatMapLatest { run ->
            if (run == null) flowOf(emptyList()) else runRepository.observeEvents(run.id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val events: StateFlow<List<ChatLine>> = combine(
        task,
        latestRun,
        persistedEvents,
        commandLine,
    ) { task, run, events, transientLine ->
        RuntimePresenter.timeline(task, run, events) + listOfNotNull(transientLine)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isRunning: StateFlow<Boolean> = combine(latestRun, startInFlight) { run, starting ->
        starting || RuntimePresenter.isRunning(run)
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val pendingApproval: StateFlow<PendingApproval?> = latestRun
        .flatMapLatest { run ->
            if (run == null) flowOf<ApprovalRequestEntity?>(null)
            else runRepository.observePendingApproval(run.id)
        }
        .map(RuntimePresenter::pendingApproval)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun bindTask(projectId: String, taskId: String) {
        if (boundTaskId.value == taskId) return
        boundTaskId.value = taskId
        commandLine.value = null
        viewModelScope.launch {
            val currentTask = taskRepository.getTaskById(taskId).first() ?: return@launch
            val currentRun = runRepository.observeLatestRunForTask(taskId).first()
            if (currentTask.status == TaskStatus.QUEUED && (currentRun == null || currentRun.status.isTerminal)) {
                runTask(projectId, taskId, currentTask.goal)
            }
        }
    }

    fun runTask(
        projectId: String,
        taskId: String,
        goal: String,
        title: String = goal.take(60),
    ) {
        if (startInFlight.value || RuntimePresenter.isRunning(latestRun.value)) return
        boundTaskId.value = taskId
        commandLine.value = null
        startInFlight.value = true
        viewModelScope.launch {
            runCatching {
                startAgentRun.execute(projectId, taskId, goal, title)
            }.onSuccess { runId ->
                requestedRunId.value = runId
            }.onFailure { error ->
                commandLine.value = ChatLine(
                    ChatLine.Role.ERROR,
                    error.message ?: "Unable to start task",
                )
            }
            startInFlight.value = false
        }
    }

    fun getCheckpoints(taskId: String): StateFlow<List<Checkpoint>> =
        checkpointManager.getCheckpointsForTask(taskId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rollback(checkpointId: String) {
        viewModelScope.launch {
            runCatching { checkpointManager.rollback(checkpointId) }
                .onSuccess {
                    commandLine.value = ChatLine(ChatLine.Role.SYSTEM, "Rolled back to checkpoint")
                }
                .onFailure {
                    commandLine.value = ChatLine(
                        ChatLine.Role.ERROR,
                        "Rollback failed: ${it.message}",
                    )
                }
        }
    }

    fun cancelCurrent() {
        val run = latestRun.value
        val runId = run?.takeUnless { it.status.isTerminal }?.id ?: requestedRunId.value ?: return
        commandLine.value = null
        runLauncher.cancel(runId)
    }

    fun respondApproval(approved: Boolean) {
        val approval = pendingApproval.value ?: return
        viewModelScope.launch {
            runCatching { resolveRunApproval.execute(approval.id, approved) }
                .onFailure {
                    commandLine.value = ChatLine(
                        ChatLine.Role.ERROR,
                        it.message ?: "Unable to resolve approval",
                    )
                }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun cancelTask(taskId: String) = cancelCurrent()
}
