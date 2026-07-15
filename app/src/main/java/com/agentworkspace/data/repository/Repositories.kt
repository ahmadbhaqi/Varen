package com.agentworkspace.data.repository

import com.agentworkspace.data.db.dao.*
import com.agentworkspace.data.db.entity.*
import com.agentworkspace.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface UsageRepository {
    fun getAllUsage(): Flow<List<UsageRecord>>
    fun getRecentUsage(limit: Int): Flow<List<UsageRecord>>
    fun getUsageForTask(taskId: String): Flow<List<UsageRecord>>
    fun getUsageForProject(projectId: String): Flow<List<UsageRecord>>
    fun getUsageForModel(modelId: String): Flow<List<UsageRecord>>
    fun getTotalUsage(): Flow<UsageSummary?>
    suspend fun recordUsage(record: UsageRecord)
}

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val usageDao: UsageDao,
) : UsageRepository {

    override fun getAllUsage(): Flow<List<UsageRecord>> =
        usageDao.getAllUsage().map { list -> list.map { it.toDomain() } }

    override fun getRecentUsage(limit: Int): Flow<List<UsageRecord>> =
        usageDao.getRecentUsage(limit).map { list -> list.map { it.toDomain() } }

    override fun getUsageForTask(taskId: String): Flow<List<UsageRecord>> =
        usageDao.getUsageForTask(taskId).map { list -> list.map { it.toDomain() } }

    override fun getUsageForProject(projectId: String): Flow<List<UsageRecord>> =
        usageDao.getUsageForProject(projectId).map { list -> list.map { it.toDomain() } }

    override fun getUsageForModel(modelId: String): Flow<List<UsageRecord>> =
        usageDao.getUsageForModel(modelId).map { list -> list.map { it.toDomain() } }

    override fun getTotalUsage(): Flow<UsageSummary?> =
        usageDao.getTotalUsage().map { totals ->
            totals?.let {
                UsageSummary(
                    totalRequests = it.totalRequests,
                    totalInputTokens = it.totalInputTokens,
                    totalOutputTokens = it.totalOutputTokens,
                    totalCachedTokens = it.totalCachedTokens,
                    totalReasoningTokens = it.totalReasoningTokens,
                    totalToolCalls = it.totalToolCalls,
                    totalExecutionCount = it.totalExecutionCount,
                    totalFilesRead = it.totalFilesRead,
                    totalFilesModified = it.totalFilesModified,
                    totalSearchCount = it.totalSearchCount,
                    totalDiffCount = it.totalDiffCount,
                    totalCheckpointCount = it.totalCheckpointCount,
                    totalRollbackCount = it.totalRollbackCount,
                    totalErrors = it.totalErrors,
                    totalRetries = it.totalRetries,
                    totalLatencyMs = it.totalLatencyMs,
                )
            }
        }

    override suspend fun recordUsage(record: UsageRecord) {
        usageDao.upsertUsageRecord(UsageRecordEntity.fromDomain(record))
    }
}

interface CheckpointRepository {
    fun getCheckpointsForProject(projectId: String): Flow<List<Checkpoint>>
    fun getCheckpointsForTask(taskId: String): Flow<List<Checkpoint>>
    suspend fun getCheckpointById(id: String): Checkpoint?
    suspend fun createCheckpoint(
        projectId: String,
        taskId: String?,
        scope: CheckpointScope,
        files: List<CheckpointFile>,
        reason: String,
    ): Checkpoint
    suspend fun deleteCheckpoint(id: String)
}

@Singleton
class CheckpointRepositoryImpl @Inject constructor(
    private val checkpointDao: CheckpointDao,
) : CheckpointRepository {

    private val json = Json { encodeDefaults = true }

    override fun getCheckpointsForProject(projectId: String): Flow<List<Checkpoint>> =
        checkpointDao.getCheckpointsForProject(projectId).map { list ->
            list.map { it.toDomain(json) }
        }

    override fun getCheckpointsForTask(taskId: String): Flow<List<Checkpoint>> =
        checkpointDao.getCheckpointsForTask(taskId).map { list ->
            list.map { it.toDomain(json) }
        }

    override suspend fun getCheckpointById(id: String): Checkpoint? =
        checkpointDao.getCheckpointById(id)?.toDomain(json)

    override suspend fun createCheckpoint(
        projectId: String,
        taskId: String?,
        scope: CheckpointScope,
        files: List<CheckpointFile>,
        reason: String,
    ): Checkpoint {
        val checkpoint = Checkpoint(
            projectId = projectId,
            taskId = taskId,
            scope = scope,
            files = files,
            reason = reason,
        )
        checkpointDao.upsertCheckpoint(
            CheckpointEntity(
                id = checkpoint.id,
                projectId = checkpoint.projectId,
                taskId = checkpoint.taskId,
                createdAt = checkpoint.createdAt,
                scope = checkpoint.scope,
                filesJson = json.encodeToString(checkpoint.files),
                reason = checkpoint.reason,
                isTrusted = checkpoint.isTrusted,
            )
        )
        return checkpoint
    }

    override suspend fun deleteCheckpoint(id: String) {
        checkpointDao.deleteCheckpoint(id)
    }

    private fun CheckpointEntity.toDomain(json: Json): Checkpoint = Checkpoint(
        id = id,
        projectId = projectId,
        taskId = taskId,
        createdAt = createdAt,
        scope = scope,
        files = json.decodeFromString(filesJson),
        reason = reason,
        isTrusted = isTrusted,
    )
}

interface DiffRepository {
    fun getDiffsForTask(taskId: String): Flow<List<DiffEntry>>
    fun getDiffsForProject(projectId: String): Flow<List<DiffEntry>>
    fun getDiffById(id: String): Flow<DiffEntry?>
    suspend fun getDiffByIdOnce(id: String): DiffEntry?
    suspend fun createDiff(
        taskId: String,
        projectId: String,
        filePath: String,
        originalContent: String,
        newContent: String,
        diffPatch: String,
    ): DiffEntry
    suspend fun updateDiffStatus(id: String, status: DiffStatus, accepted: Boolean?)
}

@Singleton
class DiffRepositoryImpl @Inject constructor(
    private val diffDao: DiffDao,
) : DiffRepository {

    override fun getDiffsForTask(taskId: String): Flow<List<DiffEntry>> =
        diffDao.getDiffsForTask(taskId).map { list -> list.map { it.toDomain() } }

    override fun getDiffsForProject(projectId: String): Flow<List<DiffEntry>> =
        diffDao.getDiffsForProject(projectId).map { list -> list.map { it.toDomain() } }

    override fun getDiffById(id: String): Flow<DiffEntry?> =
        diffDao.getDiffByIdFlow(id).map { it?.toDomain() }

    override suspend fun getDiffByIdOnce(id: String): DiffEntry? =
        diffDao.getDiffById(id)?.toDomain()

    override suspend fun createDiff(
        taskId: String,
        projectId: String,
        filePath: String,
        originalContent: String,
        newContent: String,
        diffPatch: String,
    ): DiffEntry {
        val diff = DiffEntry(
            taskId = taskId,
            projectId = projectId,
            filePath = filePath,
            originalContent = originalContent,
            newContent = newContent,
            diffPatch = diffPatch,
        )
        diffDao.upsertDiff(
            DiffEntryEntity(
                id = diff.id,
                taskId = diff.taskId,
                projectId = diff.projectId,
                filePath = diff.filePath,
                originalContent = diff.originalContent,
                newContent = diff.newContent,
                diffPatch = diff.diffPatch,
                createdAt = diff.createdAt,
                status = diff.status,
                accepted = diff.accepted,
            )
        )
        return diff
    }

    override suspend fun updateDiffStatus(id: String, status: DiffStatus, accepted: Boolean?) {
        diffDao.updateDiffStatus(id, status, accepted)
    }

    private fun DiffEntryEntity.toDomain(): DiffEntry = DiffEntry(
        id = id,
        taskId = taskId,
        projectId = projectId,
        filePath = filePath,
        originalContent = originalContent,
        newContent = newContent,
        diffPatch = diffPatch,
        createdAt = createdAt,
        status = status,
        accepted = accepted,
    )
}

interface HistoryRepository {
    fun getRecentHistory(limit: Int): Flow<List<HistoryEntry>>
    fun getHistoryForProject(projectId: String): Flow<List<HistoryEntry>>
    fun getHistoryForTask(taskId: String): Flow<List<HistoryEntry>>
    suspend fun recordHistory(entry: HistoryEntry)
}

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao,
) : HistoryRepository {

    override fun getRecentHistory(limit: Int): Flow<List<HistoryEntry>> =
        historyDao.getRecentHistory(limit).map { list -> list.map { it.toDomain() } }

    override fun getHistoryForProject(projectId: String): Flow<List<HistoryEntry>> =
        historyDao.getHistoryForProject(projectId).map { list -> list.map { it.toDomain() } }

    override fun getHistoryForTask(taskId: String): Flow<List<HistoryEntry>> =
        historyDao.getHistoryForTask(taskId).map { list -> list.map { it.toDomain() } }

    override suspend fun recordHistory(entry: HistoryEntry) {
        historyDao.upsertHistory(
            HistoryEntryEntity(
                id = entry.id,
                projectId = entry.projectId,
                taskId = entry.taskId,
                timestamp = entry.timestamp,
                type = entry.type,
                modelUsed = entry.modelUsed,
                connectionUsed = entry.connectionUsed,
                filesTouched = entry.filesTouched,
                toolCalled = entry.toolCalled,
                description = entry.description,
                usageConsumed = entry.usageConsumed,
                approvalState = entry.approvalState,
                checkpointState = entry.checkpointState,
                rollbackState = entry.rollbackState,
                fallbackState = entry.fallbackState,
                success = entry.success,
                details = entry.details,
            )
        )
    }

    private fun HistoryEntryEntity.toDomain(): HistoryEntry = HistoryEntry(
        id = id,
        projectId = projectId,
        taskId = taskId,
        timestamp = timestamp,
        type = type,
        modelUsed = modelUsed,
        connectionUsed = connectionUsed,
        filesTouched = filesTouched,
        toolCalled = toolCalled,
        description = description,
        usageConsumed = usageConsumed,
        approvalState = approvalState,
        checkpointState = checkpointState,
        rollbackState = rollbackState,
        fallbackState = fallbackState,
        success = success,
        details = details,
    )
}
