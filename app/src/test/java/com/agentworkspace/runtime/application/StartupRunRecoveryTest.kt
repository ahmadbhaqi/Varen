package com.agentworkspace.runtime.application

import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.runtime.data.RunRecoveryStore
import com.agentworkspace.runtime.domain.RunStatus
import com.agentworkspace.runtime.service.RunLauncher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupRunRecoveryTest {
    @Test
    fun `fresh process clears stale leases and resumes every active run`() = runTest {
        val store = RecordingRecoveryStore(listOf(run("run-1"), run("run-2")))
        val launcher = RecordingRunLauncher()

        StartupRunRecovery(store, launcher).execute()

        assertEquals(1, store.prepareCalls)
        assertEquals(listOf("run-1", "run-2"), launcher.resumed)
    }

    @Test
    fun `fresh process starts no service when there is nothing to recover`() = runTest {
        val store = RecordingRecoveryStore(emptyList())
        val launcher = RecordingRunLauncher()

        StartupRunRecovery(store, launcher).execute()

        assertEquals(emptyList<String>(), launcher.resumed)
    }

    private class RecordingRecoveryStore(
        private val runs: List<AgentRunEntity>,
    ) : RunRecoveryStore {
        var prepareCalls = 0

        override suspend fun prepareProcessRecovery(): List<AgentRunEntity> {
            prepareCalls++
            return runs
        }
    }

    private class RecordingRunLauncher : RunLauncher {
        val resumed = mutableListOf<String>()

        override fun start(runId: String) = Unit
        override fun resume(runId: String) {
            resumed += runId
        }
        override fun pause(runId: String) = Unit
        override fun cancel(runId: String) = Unit
    }

    private fun run(id: String) = AgentRunEntity(
        id = id,
        taskId = "task-$id",
        projectId = "project-$id",
        status = RunStatus.CALLING_MODEL,
        connectionId = "connection",
        providerModelId = "model",
        workspaceId = "content://workspace",
        configurationJson = "{}",
        leaseOwnerId = "dead-process",
        leaseExpiresAt = Long.MAX_VALUE,
        createdAt = 1,
        updatedAt = 1,
    )
}
