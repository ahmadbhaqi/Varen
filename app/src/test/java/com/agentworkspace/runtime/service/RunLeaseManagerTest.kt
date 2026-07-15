package com.agentworkspace.runtime.service

import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.runtime.data.RunLeaseStore
import com.agentworkspace.runtime.domain.IdGenerator
import com.agentworkspace.runtime.domain.RunStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunLeaseManagerTest {
    @Test
    fun `claim and heartbeat use one stable owner and ttl`() = runTest {
        val store = RecordingLeaseStore()
        val manager = RunLeaseManager(store, IdGenerator { "owner-1" })

        assertTrue(manager.claim("run-1"))
        assertTrue(manager.heartbeat("run-1"))

        assertEquals(
            listOf(
                LeaseCall("claim", "run-1", "android-owner-1", 30_000L),
                LeaseCall("heartbeat", "run-1", "android-owner-1", 30_000L),
            ),
            store.calls,
        )
        assertEquals(10_000L, manager.heartbeatMillis)
    }

    @Test
    fun `failed store claim is surfaced without retry`() = runTest {
        val store = RecordingLeaseStore(claimed = false)
        val manager = RunLeaseManager(store, IdGenerator { "owner-1" })

        assertFalse(manager.claim("run-1"))
        assertEquals(1, store.calls.size)
    }

    @Test
    fun `recoverable runs come from durable store`() = runTest {
        val store = RecordingLeaseStore(recoverable = listOf(runEntity("run-1")))
        val manager = RunLeaseManager(store, IdGenerator { "owner-1" })

        assertEquals(listOf("run-1"), manager.findRecoverableRuns().map { it.id })
    }

    private data class LeaseCall(
        val operation: String,
        val runId: String,
        val owner: String,
        val ttlMillis: Long?,
    )

    private class RecordingLeaseStore(
        private val claimed: Boolean = true,
        private val recoverable: List<AgentRunEntity> = emptyList(),
    ) : RunLeaseStore {
        val calls = mutableListOf<LeaseCall>()

        override suspend fun claimLease(
            runId: String,
            owner: String,
            ttlMillis: Long,
        ): Boolean {
            calls += LeaseCall("claim", runId, owner, ttlMillis)
            return claimed
        }

        override suspend fun heartbeat(
            runId: String,
            owner: String,
            ttlMillis: Long,
        ): Boolean {
            calls += LeaseCall("heartbeat", runId, owner, ttlMillis)
            return true
        }

        override suspend fun releaseLease(runId: String, owner: String): Boolean {
            calls += LeaseCall("release", runId, owner, null)
            return true
        }

        override suspend fun recoverableRuns(): List<AgentRunEntity> = recoverable
    }

    private fun runEntity(id: String) = AgentRunEntity(
        id = id,
        taskId = "task",
        projectId = "project",
        status = RunStatus.RECOVERING,
        connectionId = "connection",
        providerModelId = "model",
        workspaceId = "content://workspace",
        configurationJson = "{}",
        createdAt = 1L,
        updatedAt = 1L,
    )
}
