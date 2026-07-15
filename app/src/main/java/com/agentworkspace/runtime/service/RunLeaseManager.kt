package com.agentworkspace.runtime.service

import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.runtime.data.RunLeaseStore
import com.agentworkspace.runtime.domain.IdGenerator
import javax.inject.Inject
import javax.inject.Singleton

interface RunLeaseController {
    val heartbeatMillis: Long
    suspend fun claim(runId: String): Boolean
    suspend fun heartbeat(runId: String): Boolean
    suspend fun release(runId: String): Boolean
    suspend fun findRecoverableRuns(): List<AgentRunEntity>
}

@Singleton
class RunLeaseManager @Inject constructor(
    private val store: RunLeaseStore,
    ids: IdGenerator,
) : RunLeaseController {
    val ownerId: String = "android-${ids.newId()}"
    val ttlMillis: Long = 30_000L
    override val heartbeatMillis: Long = 10_000L

    override suspend fun claim(runId: String): Boolean =
        store.claimLease(runId, ownerId, ttlMillis)

    override suspend fun heartbeat(runId: String): Boolean =
        store.heartbeat(runId, ownerId, ttlMillis)

    override suspend fun release(runId: String): Boolean =
        store.releaseLease(runId, ownerId)

    override suspend fun findRecoverableRuns(): List<AgentRunEntity> =
        store.recoverableRuns()
}
