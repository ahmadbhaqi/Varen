package com.agentworkspace.data.db.dao

import androidx.room.*
import com.agentworkspace.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_records ORDER BY timestamp DESC")
    fun getAllUsage(): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentUsage(limit: Int = 100): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records WHERE taskId = :taskId ORDER BY timestamp DESC")
    fun getUsageForTask(taskId: String): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getUsageForProject(projectId: String): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records WHERE modelId = :modelId ORDER BY timestamp DESC")
    fun getUsageForModel(modelId: String): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records WHERE session = :session ORDER BY timestamp DESC")
    fun getUsageForSession(session: String): Flow<List<UsageRecordEntity>>

    @Query("""
        SELECT
            COALESCE(SUM(requests), 0) as totalRequests,
            COALESCE(SUM(inputTokens), 0) as totalInputTokens,
            COALESCE(SUM(outputTokens), 0) as totalOutputTokens,
            COALESCE(SUM(cachedTokens), 0) as totalCachedTokens,
            COALESCE(SUM(reasoningTokens), 0) as totalReasoningTokens,
            COALESCE(SUM(toolCalls), 0) as totalToolCalls,
            COALESCE(SUM(executionCount), 0) as totalExecutionCount,
            COALESCE(SUM(filesRead), 0) as totalFilesRead,
            COALESCE(SUM(filesModified), 0) as totalFilesModified,
            COALESCE(SUM(searchCount), 0) as totalSearchCount,
            COALESCE(SUM(diffCount), 0) as totalDiffCount,
            COALESCE(SUM(checkpointCount), 0) as totalCheckpointCount,
            COALESCE(SUM(rollbackCount), 0) as totalRollbackCount,
            COALESCE(SUM(errors), 0) as totalErrors,
            COALESCE(SUM(retries), 0) as totalRetries,
            COALESCE(SUM(latencyMs), 0) as totalLatencyMs
        FROM usage_records
    """)
    fun getTotalUsage(): Flow<UsageTotals?>

    @Upsert
    suspend fun upsertUsageRecord(record: UsageRecordEntity)

    @Query("DELETE FROM usage_records WHERE timestamp < :before")
    suspend fun deleteOldRecords(before: Long)
}

data class UsageTotals(
    val totalRequests: Int,
    val totalInputTokens: Int,
    val totalOutputTokens: Int,
    val totalCachedTokens: Int,
    val totalReasoningTokens: Int,
    val totalToolCalls: Int,
    val totalExecutionCount: Int,
    val totalFilesRead: Int,
    val totalFilesModified: Int,
    val totalSearchCount: Int,
    val totalDiffCount: Int,
    val totalCheckpointCount: Int,
    val totalRollbackCount: Int,
    val totalErrors: Int,
    val totalRetries: Int,
    val totalLatencyMs: Long,
)

@Dao
interface CheckpointDao {
    @Query("SELECT * FROM checkpoints WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getCheckpointsForProject(projectId: String): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoints WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun getCheckpointsForTask(taskId: String): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoints WHERE id = :id")
    suspend fun getCheckpointById(id: String): CheckpointEntity?

    @Upsert
    suspend fun upsertCheckpoint(checkpoint: CheckpointEntity)

    @Query("DELETE FROM checkpoints WHERE id = :id")
    suspend fun deleteCheckpoint(id: String)
}

@Dao
interface DiffDao {
    @Query("SELECT * FROM diff_entries WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun getDiffsForTask(taskId: String): Flow<List<DiffEntryEntity>>

    @Query("SELECT * FROM diff_entries WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getDiffsForProject(projectId: String): Flow<List<DiffEntryEntity>>

    @Query("SELECT * FROM diff_entries WHERE id = :id")
    suspend fun getDiffById(id: String): DiffEntryEntity?

    @Query("SELECT * FROM diff_entries WHERE id = :id")
    fun getDiffByIdFlow(id: String): Flow<DiffEntryEntity?>

    @Upsert
    suspend fun upsertDiff(diff: DiffEntryEntity)

    @Query("UPDATE diff_entries SET status = :status, accepted = :accepted WHERE id = :id")
    suspend fun updateDiffStatus(id: String, status: com.agentworkspace.data.model.DiffStatus, accepted: Boolean?)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 200): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getHistoryForProject(projectId: String): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun getHistoryForTask(taskId: String): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE id = :id")
    suspend fun getHistoryById(id: String): HistoryEntryEntity?

    @Upsert
    suspend fun upsertHistory(entry: HistoryEntryEntity)

    @Query("DELETE FROM history_entries WHERE timestamp < :before")
    suspend fun deleteOldHistory(before: Long)
}
