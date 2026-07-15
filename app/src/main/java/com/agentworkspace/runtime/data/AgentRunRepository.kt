package com.agentworkspace.runtime.data

import androidx.room.withTransaction
import com.agentworkspace.data.db.AgentWorkspaceDatabase
import com.agentworkspace.data.db.dao.AgentRunDao
import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.db.entity.runtime.ApprovalRequestEntity
import com.agentworkspace.data.db.entity.runtime.RunCommandEntity
import com.agentworkspace.data.db.entity.runtime.RunEventEntity
import com.agentworkspace.data.db.entity.runtime.RunMessageEntity
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.readiness.domain.WorkspaceCapability
import com.agentworkspace.readiness.domain.WorkspaceCapabilityProfiles
import com.agentworkspace.readiness.domain.WorkspaceKind
import com.agentworkspace.runtime.domain.ApprovalStatus
import com.agentworkspace.runtime.domain.IdGenerator
import com.agentworkspace.runtime.domain.RunCommandKind
import com.agentworkspace.runtime.domain.RunCommandStatus
import com.agentworkspace.runtime.domain.RunConfiguration
import com.agentworkspace.runtime.domain.RunEventKind
import com.agentworkspace.runtime.domain.RunRecoveryPolicy
import com.agentworkspace.runtime.domain.RunStateMachine
import com.agentworkspace.runtime.domain.RunStatus
import com.agentworkspace.runtime.domain.RuntimeClock
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class StartRunRequest(
    val taskId: String,
    val projectId: String,
    val initialUserMessage: String,
    val configuration: RunConfiguration,
)

interface RunLeaseStore {
    suspend fun claimLease(runId: String, owner: String, ttlMillis: Long): Boolean
    suspend fun heartbeat(runId: String, owner: String, ttlMillis: Long): Boolean
    suspend fun releaseLease(runId: String, owner: String): Boolean
    suspend fun recoverableRuns(): List<AgentRunEntity>
}

interface ApprovalStore {
    suspend fun requestApproval(
        runId: String,
        actionType: String,
        label: String,
        reason: String,
        risk: String,
    ): ApprovalRequestEntity
    fun observeApproval(approvalId: String): Flow<ApprovalRequestEntity?>
    suspend fun resolveApproval(approvalId: String, approved: Boolean): ApprovalRequestEntity
}

interface RunTimelineStore {
    suspend fun appendEvent(runId: String, kind: RunEventKind, payloadJson: String = "{}")
    suspend fun appendMessage(runId: String, role: String, content: String, toolCallId: String? = null)
}

interface RunProgressStore {
    suspend fun getRun(runId: String): AgentRunEntity?
    suspend fun transition(runId: String, status: RunStatus, payloadJson: String = "{}")
}

interface RunRecoveryStore {
    /** Called once from a fresh app process, where every persisted lease is necessarily stale. */
    suspend fun prepareProcessRecovery(): List<AgentRunEntity>
}

fun interface RunCreator {
    suspend fun createRun(request: StartRunRequest): AgentRunEntity
}

interface AgentRunRepository : RunLeaseStore, RunCreator, ApprovalStore, RunTimelineStore, RunProgressStore {
    fun observeRun(runId: String): Flow<AgentRunEntity?>
    fun observeLatestRunForTask(taskId: String): Flow<AgentRunEntity?>
    fun observeEvents(runId: String): Flow<List<RunEventEntity>>
    fun observePendingApproval(runId: String): Flow<ApprovalRequestEntity?>
    suspend fun fail(runId: String, error: Throwable)
}

@Singleton
class RoomAgentRunRepository @Inject constructor(
    private val database: AgentWorkspaceDatabase,
    private val dao: AgentRunDao,
    private val stateMachine: RunStateMachine,
    private val recoveryPolicy: RunRecoveryPolicy,
    private val clock: RuntimeClock,
    private val ids: IdGenerator,
) : AgentRunRepository, RunRecoveryStore {
    override suspend fun createRun(request: StartRunRequest): AgentRunEntity =
        database.withTransaction {
            check(dao.countActiveRunsForProject(request.projectId) == 0) {
                "Project already has an active run"
            }
            val now = clock.nowMillis()
            val run = AgentRunEntity(
                id = ids.newId(),
                taskId = request.taskId,
                projectId = request.projectId,
                status = RunStatus.QUEUED,
                connectionId = request.configuration.connectionId,
                providerModelId = request.configuration.providerModelId,
                workspaceId = request.configuration.workspaceId,
                configurationJson = RunConfigurationCodec.encode(request.configuration),
                createdAt = now,
                updatedAt = now,
            )
            dao.insertRun(run)
            dao.insertCommand(
                RunCommandEntity(
                    id = ids.newId(),
                    runId = run.id,
                    kind = RunCommandKind.START,
                    status = RunCommandStatus.PENDING,
                    createdAt = now,
                ),
            )
            insertEvent(run.id, RunEventKind.RUN_CREATED, "{}", now)
            if (request.initialUserMessage.isNotBlank()) {
                dao.insertMessage(
                    RunMessageEntity(
                        id = ids.newId(),
                        runId = run.id,
                        sequence = dao.nextMessageSequence(run.id),
                        role = "user",
                        content = request.initialUserMessage,
                        createdAt = now,
                    ),
                )
                insertEvent(
                    run.id,
                    RunEventKind.MESSAGE,
                    buildJsonObject {
                        put("role", "user")
                        put("content", request.initialUserMessage)
                    }.toString(),
                    now,
                )
            }
            run
        }

    override suspend fun getRun(runId: String): AgentRunEntity? = dao.getRun(runId)

    override fun observeRun(runId: String): Flow<AgentRunEntity?> = dao.observeRun(runId)

    override fun observeLatestRunForTask(taskId: String): Flow<AgentRunEntity?> =
        dao.observeLatestRunForTask(taskId)

    override fun observeEvents(runId: String): Flow<List<RunEventEntity>> = dao.observeEvents(runId)

    override fun observePendingApproval(runId: String): Flow<ApprovalRequestEntity?> =
        dao.observePendingApproval(runId)

    override fun observeApproval(approvalId: String): Flow<ApprovalRequestEntity?> =
        dao.observeApproval(approvalId)

    override suspend fun transition(runId: String, status: RunStatus, payloadJson: String) {
        database.withTransaction {
            val current = dao.getRun(runId) ?: error("Run not found: $runId")
            if (current.status == status) return@withTransaction
            stateMachine.requireTransition(current.status, status)
            val now = clock.nowMillis()
            dao.updateRun(
                current.copy(
                    status = status,
                    updatedAt = now,
                    revision = current.revision + 1,
                ),
            )
            if (status in APPROVAL_EXPIRING_STATUSES) {
                dao.expirePendingApprovals(runId, now)
            }
            insertEvent(
                runId = runId,
                kind = eventKindFor(status),
                payloadJson = statusPayload(status, payloadJson),
                now = now,
            )
        }
    }

    override suspend fun appendEvent(runId: String, kind: RunEventKind, payloadJson: String) {
        database.withTransaction {
            checkNotNull(dao.getRun(runId)) { "Run not found: $runId" }
            insertEvent(runId, kind, payloadJson, clock.nowMillis())
        }
    }

    override suspend fun appendMessage(
        runId: String,
        role: String,
        content: String,
        toolCallId: String?,
    ) {
        database.withTransaction {
            checkNotNull(dao.getRun(runId)) { "Run not found: $runId" }
            dao.insertMessage(
                RunMessageEntity(
                    id = ids.newId(),
                    runId = runId,
                    sequence = dao.nextMessageSequence(runId),
                    role = role,
                    content = content,
                    toolCallId = toolCallId,
                    createdAt = clock.nowMillis(),
                ),
            )
        }
    }

    override suspend fun requestApproval(
        runId: String,
        actionType: String,
        label: String,
        reason: String,
        risk: String,
    ): ApprovalRequestEntity = database.withTransaction {
        val run = dao.getRun(runId) ?: error("Run not found: $runId")
        stateMachine.requireTransition(run.status, RunStatus.WAITING_APPROVAL)
        val now = clock.nowMillis()
        val approval = ApprovalRequestEntity(
            id = ids.newId(),
            runId = runId,
            actionType = actionType,
            label = label,
            reason = reason,
            risk = risk,
            status = ApprovalStatus.PENDING,
            requestedAt = now,
        )
        dao.insertApproval(approval)
        dao.updateRun(run.copy(status = RunStatus.WAITING_APPROVAL, updatedAt = now, revision = run.revision + 1))
        insertEvent(
            runId,
            RunEventKind.APPROVAL_REQUESTED,
            buildJsonObject {
                put("approvalId", approval.id)
                put("label", label)
                put("reason", reason)
                put("risk", risk)
            }.toString(),
            now,
        )
        approval
    }

    override suspend fun resolveApproval(
        approvalId: String,
        approved: Boolean,
    ): ApprovalRequestEntity = database.withTransaction {
        val current = dao.getApproval(approvalId) ?: error("Approval not found: $approvalId")
        check(current.status == ApprovalStatus.PENDING) { "Approval already resolved: $approvalId" }
        val now = clock.nowMillis()
        val resolved = current.copy(
            status = if (approved) ApprovalStatus.APPROVED else ApprovalStatus.DENIED,
            resolvedAt = now,
        )
        dao.updateApproval(resolved)
        insertEvent(
            current.runId,
            RunEventKind.APPROVAL_RESOLVED,
            buildJsonObject {
                put("approvalId", approvalId)
                put("approved", approved)
            }.toString(),
            now,
        )
        resolved
    }

    override suspend fun fail(runId: String, error: Throwable) {
        database.withTransaction {
            val run = dao.getRun(runId) ?: return@withTransaction
            if (run.status.isTerminal) return@withTransaction
            stateMachine.requireTransition(run.status, RunStatus.FAILED)
            val now = clock.nowMillis()
            val code = error::class.simpleName ?: "RuntimeFailure"
            val message = error.message.orEmpty().take(MAX_ERROR_MESSAGE_LENGTH)
            dao.updateRun(
                run.copy(
                    status = RunStatus.FAILED,
                    updatedAt = now,
                    revision = run.revision + 1,
                    lastErrorCode = code,
                    lastErrorMessage = message,
                ),
            )
            dao.expirePendingApprovals(runId, now)
            insertEvent(
                runId,
                RunEventKind.FAILED,
                buildJsonObject {
                    put("code", code)
                    put("message", message)
                }.toString(),
                now,
            )
        }
    }

    override suspend fun claimLease(runId: String, owner: String, ttlMillis: Long): Boolean =
        database.withTransaction {
            val now = clock.nowMillis()
            val claimed = dao.claimLease(runId, owner, now, now + ttlMillis) == 1
            if (claimed) {
                insertEvent(
                    runId,
                    RunEventKind.LEASE_ACQUIRED,
                    buildJsonObject { put("owner", owner) }.toString(),
                    now,
                )
            }
            claimed
        }

    override suspend fun heartbeat(runId: String, owner: String, ttlMillis: Long): Boolean {
        val now = clock.nowMillis()
        return dao.heartbeat(runId, owner, now, now + ttlMillis) == 1
    }

    override suspend fun releaseLease(runId: String, owner: String): Boolean =
        database.withTransaction {
            val now = clock.nowMillis()
            val released = dao.releaseLease(runId, owner, now) == 1
            if (released) {
                insertEvent(
                    runId,
                    RunEventKind.LEASE_RELEASED,
                    buildJsonObject { put("owner", owner) }.toString(),
                    now,
                )
            }
            released
        }

    override suspend fun recoverableRuns(): List<AgentRunEntity> =
        database.withTransaction {
            prepareRecoveryCandidates(
                candidates = dao.getRecoverableRuns(clock.nowMillis()),
                clearLeases = false,
            )
        }

    override suspend fun prepareProcessRecovery(): List<AgentRunEntity> =
        database.withTransaction {
            prepareRecoveryCandidates(
                candidates = dao.getProcessRecoveryCandidates(),
                clearLeases = true,
            )
        }

    private suspend fun prepareRecoveryCandidates(
        candidates: List<AgentRunEntity>,
        clearLeases: Boolean,
    ): List<AgentRunEntity> {
        val safe = candidates.filterNot { run ->
            val uncertain = recoveryPolicy.hasUncertainSideEffect(dao.getEventsOnce(run.id))
            if (uncertain) pauseUncertainRun(run)
            uncertain
        }
        if (clearLeases && safe.isNotEmpty()) {
            dao.clearLeasesForProcessRecovery(safe.map { it.id }, clock.nowMillis())
        }
        return if (clearLeases) {
            safe.map { it.copy(leaseOwnerId = null, leaseExpiresAt = null, heartbeatAt = null) }
        } else {
            safe
        }
    }

    private suspend fun pauseUncertainRun(run: AgentRunEntity) {
        stateMachine.requireTransition(run.status, RunStatus.PAUSED)
        val now = clock.nowMillis()
        dao.updateRun(
            run.copy(
                status = RunStatus.PAUSED,
                leaseOwnerId = null,
                leaseExpiresAt = null,
                heartbeatAt = null,
                revision = run.revision + 1,
                updatedAt = now,
                lastErrorCode = UNCERTAIN_SIDE_EFFECT,
                lastErrorMessage = UNCERTAIN_SIDE_EFFECT_MESSAGE,
            ),
        )
        insertEvent(
            run.id,
            RunEventKind.PAUSED,
            buildJsonObject {
                put("code", UNCERTAIN_SIDE_EFFECT)
                put("message", UNCERTAIN_SIDE_EFFECT_MESSAGE)
            }.toString(),
            now,
        )
    }

    private suspend fun insertEvent(
        runId: String,
        kind: RunEventKind,
        payloadJson: String,
        now: Long,
    ) {
        dao.insertEvent(
            RunEventEntity(
                id = ids.newId(),
                runId = runId,
                sequence = dao.nextEventSequence(runId),
                kind = kind,
                payloadJson = payloadJson,
                createdAt = now,
            ),
        )
    }

    private fun eventKindFor(status: RunStatus): RunEventKind = when (status) {
        RunStatus.PAUSED -> RunEventKind.PAUSED
        RunStatus.CANCELLED -> RunEventKind.CANCELLED
        RunStatus.COMPLETED -> RunEventKind.COMPLETED
        RunStatus.FAILED -> RunEventKind.FAILED
        RunStatus.RECOVERING -> RunEventKind.RECOVERY_STARTED
        else -> RunEventKind.STATUS_CHANGED
    }

    private fun statusPayload(status: RunStatus, payloadJson: String): String {
        val supplied = runCatching { JSON.parseToJsonElement(payloadJson) as? JsonObject }
            .getOrNull()
            .orEmpty()
        return buildJsonObject {
            put("status", status.name)
            supplied.forEach { (key, value) -> put(key, value) }
        }.toString()
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        const val MAX_ERROR_MESSAGE_LENGTH = 500
        const val UNCERTAIN_SIDE_EFFECT = "UNCERTAIN_SIDE_EFFECT"
        const val UNCERTAIN_SIDE_EFFECT_MESSAGE =
            "A tool was interrupted before its outcome was recorded. Review the workspace before resuming."
        val APPROVAL_EXPIRING_STATUSES = setOf(
            RunStatus.CANCELLED,
            RunStatus.FAILED,
            RunStatus.ROLLED_BACK,
        )
    }
}

object RunConfigurationCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(configuration: RunConfiguration): String = buildJsonObject {
        put("connectionId", configuration.connectionId)
        put("providerModelId", configuration.providerModelId)
        put("workspaceId", configuration.workspaceId)
        put("trustMode", configuration.trustMode.name)
        put("transport", configuration.transport)
        put("systemPromptVersion", configuration.systemPromptVersion)
        put(
            "workspaceKind",
            configuration.workspaceKind.name,
        )
        put(
            "capabilities",
            JsonArray(configuration.capabilities.sortedBy { it.name }.map { JsonPrimitive(it.name) }),
        )
        put(
            "limitations",
            JsonArray(configuration.limitations.map(::JsonPrimitive)),
        )
    }.toString()

    fun decode(raw: String): RunConfiguration {
        val value = json.parseToJsonElement(raw) as JsonObject
        val workspaceId = value.requiredString("workspaceId")
        val workspaceKind = value["workspaceKind"]?.jsonPrimitive?.contentOrNull
            ?.let { rawKind -> runCatching { WorkspaceKind.valueOf(rawKind) }.getOrNull() }
            ?: if (workspaceId.startsWith("github://")) WorkspaceKind.GITHUB else WorkspaceKind.LOCAL_SAF
        val safeDefaults = WorkspaceCapabilityProfiles.forKind(workspaceKind)
        val capabilities = (value["capabilities"] as? JsonArray)?.mapNotNull { item ->
            item.jsonPrimitive.contentOrNull
                ?.let { rawCapability ->
                    runCatching { WorkspaceCapability.valueOf(rawCapability) }.getOrNull()
                }
        }?.toSet() ?: safeDefaults.capabilities
        val limitations = (value["limitations"] as? JsonArray)?.mapNotNull {
            it.jsonPrimitive.contentOrNull
        } ?: if (workspaceKind == WorkspaceKind.LOCAL_SAF) {
            listOf("COMMAND_EXECUTION_UNAVAILABLE")
        } else {
            emptyList()
        }
        return RunConfiguration(
            connectionId = value.requiredString("connectionId"),
            providerModelId = value.requiredString("providerModelId"),
            workspaceId = workspaceId,
            trustMode = TrustMode.valueOf(value.requiredString("trustMode")),
            transport = value.requiredString("transport"),
            systemPromptVersion = value["systemPromptVersion"]?.jsonPrimitive?.contentOrNull
                ?: "agent-workspace-v1",
            workspaceKind = workspaceKind,
            capabilities = capabilities,
            limitations = limitations,
        )
    }

    private fun JsonObject.requiredString(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: error("Missing run configuration field: $key")
}
