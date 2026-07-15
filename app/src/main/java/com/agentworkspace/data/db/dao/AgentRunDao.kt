package com.agentworkspace.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.db.entity.runtime.ApprovalRequestEntity
import com.agentworkspace.data.db.entity.runtime.RunCommandEntity
import com.agentworkspace.data.db.entity.runtime.RunEventEntity
import com.agentworkspace.data.db.entity.runtime.RunMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentRunDao {
    @Query("SELECT * FROM agent_runs WHERE id = :runId")
    fun observeRun(runId: String): Flow<AgentRunEntity?>

    @Query("SELECT * FROM agent_runs WHERE id = :runId")
    suspend fun getRun(runId: String): AgentRunEntity?

    @Query("SELECT * FROM agent_runs WHERE taskId = :taskId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestRunForTask(taskId: String): Flow<AgentRunEntity?>

    @Query("SELECT * FROM run_events WHERE runId = :runId ORDER BY sequence")
    fun observeEvents(runId: String): Flow<List<RunEventEntity>>

    @Query("SELECT * FROM run_events WHERE runId = :runId ORDER BY sequence")
    suspend fun getEventsOnce(runId: String): List<RunEventEntity>

    @Query("SELECT * FROM run_commands WHERE runId = :runId ORDER BY createdAt")
    suspend fun getCommands(runId: String): List<RunCommandEntity>

    @Query(
        "SELECT * FROM approval_requests " +
            "WHERE runId = :runId AND status = 'PENDING' " +
            "ORDER BY requestedAt LIMIT 1",
    )
    fun observePendingApproval(runId: String): Flow<ApprovalRequestEntity?>

    @Query("SELECT * FROM approval_requests WHERE id = :approvalId")
    fun observeApproval(approvalId: String): Flow<ApprovalRequestEntity?>

    @Query("SELECT * FROM approval_requests WHERE id = :approvalId")
    suspend fun getApproval(approvalId: String): ApprovalRequestEntity?

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM run_events WHERE runId = :runId")
    suspend fun nextEventSequence(runId: String): Long

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM run_messages WHERE runId = :runId")
    suspend fun nextMessageSequence(runId: String): Long

    @Query(
        "SELECT COUNT(*) FROM agent_runs WHERE projectId = :projectId " +
            "AND status NOT IN ('COMPLETED','FAILED','CANCELLED','ROLLED_BACK')",
    )
    suspend fun countActiveRunsForProject(projectId: String): Int

    @Insert
    suspend fun insertRun(run: AgentRunEntity)

    @Insert
    suspend fun insertCommand(command: RunCommandEntity)

    @Insert
    suspend fun insertEvent(event: RunEventEntity)

    @Insert
    suspend fun insertMessage(message: RunMessageEntity)

    @Insert
    suspend fun insertApproval(approval: ApprovalRequestEntity)

    @Update
    suspend fun updateRun(run: AgentRunEntity)

    @Update
    suspend fun updateApproval(approval: ApprovalRequestEntity)

    @Query(
        "UPDATE approval_requests SET status = 'EXPIRED', resolvedAt = :now " +
            "WHERE runId = :runId AND status = 'PENDING'",
    )
    suspend fun expirePendingApprovals(runId: String, now: Long): Int

    @Query(
        "UPDATE agent_runs SET leaseOwnerId = :owner, leaseExpiresAt = :expiresAt, " +
            "heartbeatAt = :now, revision = revision + 1, updatedAt = :now " +
            "WHERE id = :runId AND " +
            "(leaseOwnerId IS NULL OR leaseOwnerId = :owner OR leaseExpiresAt < :now)",
    )
    suspend fun claimLease(runId: String, owner: String, now: Long, expiresAt: Long): Int

    @Query(
        "UPDATE agent_runs SET heartbeatAt = :now, leaseExpiresAt = :expiresAt, " +
            "revision = revision + 1, updatedAt = :now " +
            "WHERE id = :runId AND leaseOwnerId = :owner",
    )
    suspend fun heartbeat(runId: String, owner: String, now: Long, expiresAt: Long): Int

    @Query(
        "UPDATE agent_runs SET leaseOwnerId = NULL, leaseExpiresAt = NULL, heartbeatAt = NULL, " +
            "revision = revision + 1, updatedAt = :now " +
            "WHERE id = :runId AND leaseOwnerId = :owner",
    )
    suspend fun releaseLease(runId: String, owner: String, now: Long): Int

    @Query(
        "SELECT * FROM agent_runs " +
            "WHERE status NOT IN " +
            "('COMPLETED','FAILED','CANCELLED','ROLLED_BACK','WAITING_APPROVAL','PAUSED') " +
            "AND (leaseOwnerId IS NULL OR leaseExpiresAt < :now)",
    )
    suspend fun getRecoverableRuns(now: Long): List<AgentRunEntity>

    @Query(
        "SELECT * FROM agent_runs " +
            "WHERE status NOT IN " +
            "('COMPLETED','FAILED','CANCELLED','ROLLED_BACK','WAITING_APPROVAL','PAUSED')",
    )
    suspend fun getProcessRecoveryCandidates(): List<AgentRunEntity>

    @Query(
        "UPDATE agent_runs SET leaseOwnerId = NULL, leaseExpiresAt = NULL, heartbeatAt = NULL, " +
            "revision = revision + 1, updatedAt = :now WHERE id IN (:runIds)",
    )
    suspend fun clearLeasesForProcessRecovery(runIds: List<String>, now: Long): Int
}
