package com.agentworkspace.runtime.service

import com.agentworkspace.runtime.data.AgentRunRepository
import com.agentworkspace.runtime.domain.RunStatus
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class RunSupervisor @Inject constructor(
    private val repository: AgentRunRepository,
    private val leases: RunLeaseController,
    private val executor: RunExecutor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val requestedStops = ConcurrentHashMap<String, RunStatus>()

    fun startOrRecover(runId: String) {
        if (jobs.containsKey(runId)) return
        val job = scope.launch(start = CoroutineStart.LAZY) { executeOnce(runId) }
        val existing = jobs.putIfAbsent(runId, job)
        if (existing != null) {
            job.cancel()
            return
        }
        job.invokeOnCompletion { jobs.remove(runId, job) }
        job.start()
    }

    internal suspend fun executeOnce(runId: String) = coroutineScope {
        if (!leases.claim(runId)) return@coroutineScope
        try {
            val run = repository.getRun(runId) ?: return@coroutineScope
            if (run.status == RunStatus.QUEUED) {
                repository.transition(runId, RunStatus.PLANNING)
                repository.transition(runId, RunStatus.READING_CONTEXT)
                repository.transition(runId, RunStatus.CALLING_MODEL)
            } else {
                repository.transition(runId, RunStatus.RECOVERING)
                repository.transition(runId, RunStatus.CALLING_MODEL)
            }

            val heartbeat = launch {
                while (isActive) {
                    delay(leases.heartbeatMillis)
                    check(leases.heartbeat(runId)) { "Run lease lost" }
                }
            }
            try {
                executor.execute(runId)
                repository.transition(runId, RunStatus.COMPLETED)
            } finally {
                heartbeat.cancelAndJoin()
            }
        } catch (cancelled: CancellationException) {
            repository.transition(
                runId,
                requestedStops.remove(runId) ?: RunStatus.CANCELLED,
            )
            throw cancelled
        } catch (error: Exception) {
            repository.fail(runId, error)
        } finally {
            leases.release(runId)
        }
    }

    suspend fun pause(runId: String) {
        stop(runId, RunStatus.PAUSED)
    }

    suspend fun cancel(runId: String) {
        stop(runId, RunStatus.CANCELLED)
    }

    suspend fun recoverExpiredRuns(): List<String> {
        val runIds = leases.findRecoverableRuns().map { it.id }
        runIds.forEach(::startOrRecover)
        return runIds
    }

    private suspend fun stop(runId: String, target: RunStatus) {
        requestedStops[runId] = target
        val job = jobs.remove(runId)
        if (job == null) {
            requestedStops.remove(runId)
            repository.transition(runId, target)
        } else {
            job.cancelAndJoin()
        }
    }
}
