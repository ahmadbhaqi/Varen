package com.agentworkspace.runtime.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.agentworkspace.data.repository.TaskRepository
import com.agentworkspace.runtime.data.AgentRunRepository
import com.agentworkspace.runtime.domain.RunStatus
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AgentRunService : Service() {
    @Inject lateinit var supervisor: RunSupervisor
    @Inject lateinit var notifications: RunNotificationController
    @Inject lateinit var runs: AgentRunRepository
    @Inject lateinit var tasks: TaskRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val observers = ConcurrentHashMap<String, Job>()
    private val activeRuns = ConcurrentHashMap<String, RunStatus>()
    private val runTitles = ConcurrentHashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val runId = intent?.getStringExtra(EXTRA_RUN_ID)
        val notificationRunId = runId ?: RECOVERY_NOTIFICATION_KEY
        val initialStatus = RunServicePolicy.initialStatus(action)
        startForeground(
            notifications.notificationId(notificationRunId),
            notifications.build(notificationRunId, "AgentWorkspace is working", initialStatus),
        )

        if (runId != null) {
            if (RunServicePolicy.keepsServiceActive(initialStatus)) {
                activeRuns[runId] = initialStatus
            }
            observeRun(runId)
        }

        when (action) {
            ACTION_START, ACTION_RESUME -> if (runId != null) supervisor.startOrRecover(runId)
            ACTION_PAUSE -> if (runId != null) serviceScope.launch { supervisor.pause(runId) }
            ACTION_CANCEL -> if (runId != null) serviceScope.launch { supervisor.cancel(runId) }
            else -> recoverAfterStickyRestart()
        }
        return START_STICKY
    }

    private fun recoverAfterStickyRestart() {
        serviceScope.launch {
            val recovered = supervisor.recoverExpiredRuns()
            if (recovered.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            recovered.forEach { runId ->
                activeRuns[runId] = RunStatus.RECOVERING
                observeRun(runId)
            }
            val foregroundRun = recovered.first()
            startForeground(
                notifications.notificationId(foregroundRun),
                notifications.build(foregroundRun, "Restoring agent task", RunStatus.RECOVERING),
            )
            notifications.cancel(RECOVERY_NOTIFICATION_KEY)
        }
    }

    private fun observeRun(runId: String) {
        if (observers.containsKey(runId)) return
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            var inactiveStatus: RunStatus? = null
            try {
                val run = runs.getRun(runId) ?: run {
                    activeRuns.remove(runId)
                    inactiveStatus = RunStatus.FAILED
                    return@launch
                }
                val title = tasks.getTaskById(run.taskId).first()?.title
                    ?.takeIf { it.isNotBlank() }
                    ?: "Agent task"
                runTitles[runId] = title
                runs.observeRun(runId)
                    .filterNotNull()
                    .takeWhile { current ->
                        notifications.show(runId, title, current.status)
                        if (RunServicePolicy.keepsServiceActive(current.status)) {
                            activeRuns[runId] = current.status
                            true
                        } else {
                            activeRuns.remove(runId)
                            inactiveStatus = current.status
                            false
                        }
                    }
                    .collect {}
            } finally {
                observers.remove(runId)
                inactiveStatus?.let { finishInactiveRun(runId, it) }
            }
        }
        val previous = observers.putIfAbsent(runId, job)
        if (previous == null) job.start() else job.cancel()
    }

    private fun finishInactiveRun(runId: String, status: RunStatus) {
        if (status.isTerminal) notifications.cancel(runId)
        if (activeRuns.isEmpty()) {
            stopForeground(
                if (status == RunStatus.PAUSED) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE,
            )
            stopSelf()
            return
        }
        val next = activeRuns.entries.first()
        startForeground(
            notifications.notificationId(next.key),
            notifications.build(
                next.key,
                runTitles[next.key] ?: "Agent task",
                next.value,
            ),
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.agentworkspace.runtime.START"
        const val ACTION_PAUSE = "com.agentworkspace.runtime.PAUSE"
        const val ACTION_RESUME = "com.agentworkspace.runtime.RESUME"
        const val ACTION_CANCEL = "com.agentworkspace.runtime.CANCEL"
        const val EXTRA_RUN_ID = "run_id"

        private const val RECOVERY_NOTIFICATION_KEY = "runtime-recovery"
    }
}
