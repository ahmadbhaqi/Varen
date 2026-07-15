package com.agentworkspace.runtime.service

import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.db.entity.runtime.ApprovalRequestEntity
import com.agentworkspace.data.db.entity.runtime.RunEventEntity
import com.agentworkspace.runtime.data.AgentRunRepository
import com.agentworkspace.runtime.data.StartRunRequest
import com.agentworkspace.runtime.domain.RunEventKind
import com.agentworkspace.runtime.domain.RunStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSupervisorTest {
    @Test
    fun `successful execution advances lifecycle and releases lease`() = runTest {
        val repository = InMemorySupervisorRepository(runEntity(RunStatus.QUEUED))
        val leases = FakeLeaseController()
        val executor = RecordingExecutor()
        val supervisor = RunSupervisor(repository, leases, executor)

        supervisor.executeOnce("run-1")

        assertEquals(listOf("run-1"), executor.executedRuns)
        assertEquals(
            listOf(
                RunStatus.PLANNING,
                RunStatus.READING_CONTEXT,
                RunStatus.CALLING_MODEL,
                RunStatus.COMPLETED,
            ),
            repository.transitions,
        )
        assertEquals(listOf("run-1"), leases.releasedRuns)
    }

    @Test
    fun `coroutine cancellation persists cancelled and is rethrown`() = runTest {
        val repository = InMemorySupervisorRepository(runEntity(RunStatus.CALLING_MODEL))
        val leases = FakeLeaseController()
        val supervisor = RunSupervisor(
            repository,
            leases,
            RecordingExecutor(CancellationException("user cancelled")),
        )

        var failure: Throwable? = null
        try {
            supervisor.executeOnce("run-1")
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is CancellationException)
        assertEquals(RunStatus.CANCELLED, repository.run.status)
        assertEquals(listOf("run-1"), leases.releasedRuns)
    }

    @Test
    fun `ordinary exception persists failed`() = runTest {
        val repository = InMemorySupervisorRepository(runEntity(RunStatus.QUEUED))
        val error = IOException("network unavailable")
        val supervisor = RunSupervisor(
            repository,
            FakeLeaseController(),
            RecordingExecutor(error),
        )

        supervisor.executeOnce("run-1")

        assertEquals(RunStatus.FAILED, repository.run.status)
        assertEquals(error, repository.failure)
    }

    private class RecordingExecutor(
        private val failure: Throwable? = null,
    ) : RunExecutor {
        val executedRuns = mutableListOf<String>()

        override suspend fun execute(runId: String) {
            executedRuns += runId
            failure?.let { throw it }
        }
    }

    private class FakeLeaseController(
        private val claimResult: Boolean = true,
    ) : RunLeaseController {
        override val heartbeatMillis: Long = 10_000L
        val releasedRuns = mutableListOf<String>()

        override suspend fun claim(runId: String): Boolean = claimResult

        override suspend fun heartbeat(runId: String): Boolean = true

        override suspend fun release(runId: String): Boolean {
            releasedRuns += runId
            return true
        }

        override suspend fun findRecoverableRuns(): List<AgentRunEntity> = emptyList()
    }

    private class InMemorySupervisorRepository(
        initial: AgentRunEntity,
    ) : AgentRunRepository {
        var run: AgentRunEntity = initial
        var failure: Throwable? = null
        val transitions = mutableListOf<RunStatus>()

        override suspend fun getRun(runId: String): AgentRunEntity = run

        override suspend fun transition(runId: String, status: RunStatus, payloadJson: String) {
            transitions += status
            run = run.copy(status = status)
        }

        override suspend fun fail(runId: String, error: Throwable) {
            failure = error
            run = run.copy(status = RunStatus.FAILED)
        }

        override suspend fun createRun(request: StartRunRequest): AgentRunEntity = error("Not used")
        override fun observeRun(runId: String): Flow<AgentRunEntity?> = error("Not used")
        override fun observeLatestRunForTask(taskId: String): Flow<AgentRunEntity?> = error("Not used")
        override fun observeEvents(runId: String): Flow<List<RunEventEntity>> = error("Not used")
        override fun observePendingApproval(runId: String): Flow<ApprovalRequestEntity?> = error("Not used")
        override fun observeApproval(approvalId: String): Flow<ApprovalRequestEntity?> = error("Not used")
        override suspend fun appendEvent(runId: String, kind: RunEventKind, payloadJson: String) = Unit
        override suspend fun appendMessage(runId: String, role: String, content: String, toolCallId: String?) = Unit
        override suspend fun requestApproval(runId: String, actionType: String, label: String, reason: String, risk: String): ApprovalRequestEntity = error("Not used")
        override suspend fun resolveApproval(approvalId: String, approved: Boolean): ApprovalRequestEntity = error("Not used")
        override suspend fun claimLease(runId: String, owner: String, ttlMillis: Long): Boolean = error("Not used")
        override suspend fun heartbeat(runId: String, owner: String, ttlMillis: Long): Boolean = error("Not used")
        override suspend fun releaseLease(runId: String, owner: String): Boolean = error("Not used")
        override suspend fun recoverableRuns(): List<AgentRunEntity> = error("Not used")
    }

    private fun runEntity(status: RunStatus) = AgentRunEntity(
        id = "run-1",
        taskId = "task",
        projectId = "project",
        status = status,
        connectionId = "connection",
        providerModelId = "gemini-2.5-pro",
        workspaceId = "content://demo",
        configurationJson = "{}",
        createdAt = 1L,
        updatedAt = 1L,
    )
}
