package com.agentworkspace.data.db

import androidx.room.TypeConverter
import com.agentworkspace.data.model.*
import com.agentworkspace.runtime.domain.ApprovalStatus
import com.agentworkspace.runtime.domain.RunCommandKind
import com.agentworkspace.runtime.domain.RunCommandStatus
import com.agentworkspace.runtime.domain.RunEventKind
import com.agentworkspace.runtime.domain.RunStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── String List ──
    @TypeConverter
    fun stringListToString(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun stringToStringList(value: String): List<String> = json.decodeFromString(value)

    // ── TrustMode ──
    @TypeConverter
    fun trustModeToString(value: TrustMode): String = value.name

    @TypeConverter
    fun stringToTrustMode(value: String): TrustMode = TrustMode.valueOf(value)

    // ── TaskStatus ──
    @TypeConverter
    fun taskStatusToString(value: TaskStatus): String = value.name

    @TypeConverter
    fun stringToTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    // ── AuthScheme ──
    @TypeConverter
    fun authSchemeToString(value: AuthScheme): String = value.name
    @TypeConverter
    fun stringToAuthScheme(value: String): AuthScheme = AuthScheme.valueOf(value)
    // ── ProviderType ──
    @TypeConverter
    fun providerTypeToString(value: ProviderType): String = value.name

    @TypeConverter
    fun stringToProviderType(value: String): ProviderType = ProviderType.valueOf(value)

    // ── AuthState ──
    @TypeConverter
    fun authStateToString(value: AuthState): String = value.name

    @TypeConverter
    fun stringToAuthState(value: String): AuthState = AuthState.valueOf(value)

    // ── AvailabilityState ──
    @TypeConverter
    fun availabilityStateToString(value: AvailabilityState): String = value.name

    @TypeConverter
    fun stringToAvailabilityState(value: String): AvailabilityState = AvailabilityState.valueOf(value)

    // ── ModelCapabilities ──
    @TypeConverter
    fun modelCapabilitiesToString(value: ModelCapabilities): String = json.encodeToString(value)

    @TypeConverter
    fun stringToModelCapabilities(value: String): ModelCapabilities = json.decodeFromString(value)

    // ── DiffStatus ──
    @TypeConverter
    fun diffStatusToString(value: DiffStatus): String = value.name

    @TypeConverter
    fun stringToDiffStatus(value: String): DiffStatus = DiffStatus.valueOf(value)

    // ── CheckpointScope ──
    @TypeConverter
    fun checkpointScopeToString(value: CheckpointScope): String = value.name

    @TypeConverter
    fun stringToCheckpointScope(value: String): CheckpointScope = CheckpointScope.valueOf(value)

    // ── HistoryType ──
    @TypeConverter
    fun historyTypeToString(value: HistoryType): String = value.name

    @TypeConverter
    fun stringToHistoryType(value: String): HistoryType = HistoryType.valueOf(value)

    // ── TaskUsage ──
    @TypeConverter
    fun taskUsageToString(value: TaskUsage): String = json.encodeToString(value)

    @TypeConverter
    fun stringToTaskUsage(value: String): TaskUsage = json.decodeFromString(value)

    // ── ToolCall List ──
    @TypeConverter
    fun toolCallListToString(value: List<ToolCall>): String = json.encodeToString(value)

    @TypeConverter
    fun stringToToolCallList(value: String): List<ToolCall> = json.decodeFromString(value)

    // ── ApprovalRecord List ──
    @TypeConverter
    fun approvalRecordListToString(value: List<ApprovalRecord>): String = json.encodeToString(value)

    @TypeConverter
    fun stringToApprovalRecordList(value: String): List<ApprovalRecord> = json.decodeFromString(value)

    // ── AgentStep List ──
    @TypeConverter
    fun agentStepListToString(value: List<AgentStep>): String = json.encodeToString(value)

    @TypeConverter
    fun stringToAgentStepList(value: String): List<AgentStep> = json.decodeFromString(value)

    // ── StepStatus ──
    @TypeConverter
    fun stepStatusToString(value: StepStatus): String = value.name

    @TypeConverter
    fun stringToStepStatus(value: String): StepStatus = StepStatus.valueOf(value)

    @TypeConverter
    fun runStatusToString(value: RunStatus): String = value.name

    @TypeConverter
    fun stringToRunStatus(value: String): RunStatus = RunStatus.valueOf(value)

    @TypeConverter
    fun runCommandKindToString(value: RunCommandKind): String = value.name

    @TypeConverter
    fun stringToRunCommandKind(value: String): RunCommandKind = RunCommandKind.valueOf(value)

    @TypeConverter
    fun runCommandStatusToString(value: RunCommandStatus): String = value.name

    @TypeConverter
    fun stringToRunCommandStatus(value: String): RunCommandStatus = RunCommandStatus.valueOf(value)

    @TypeConverter
    fun runEventKindToString(value: RunEventKind): String = value.name

    @TypeConverter
    fun stringToRunEventKind(value: String): RunEventKind = RunEventKind.valueOf(value)

    @TypeConverter
    fun approvalStatusToString(value: ApprovalStatus): String = value.name

    @TypeConverter
    fun stringToApprovalStatus(value: String): ApprovalStatus = ApprovalStatus.valueOf(value)
}
