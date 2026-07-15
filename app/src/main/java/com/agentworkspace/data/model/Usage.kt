package com.agentworkspace.data.model

import java.util.UUID

/**
 * Usage is a major product feature.
 * Usage is treated as persisted state, not disposable log output.
 */
data class UsageRecord(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val projectId: String? = null,
    val modelId: String? = null,
    val connectionId: String? = null,
    val session: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val requests: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val toolCalls: Int = 0,
    val executionCount: Int = 0,
    val filesRead: Int = 0,
    val filesModified: Int = 0,
    val searchCount: Int = 0,
    val diffCount: Int = 0,
    val checkpointCount: Int = 0,
    val rollbackCount: Int = 0,
    val errors: Int = 0,
    val retries: Int = 0,
    val latencyMs: Long = 0,
)

data class UsageSummary(
    val totalRequests: Int = 0,
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalCachedTokens: Int = 0,
    val totalReasoningTokens: Int = 0,
    val totalToolCalls: Int = 0,
    val totalExecutionCount: Int = 0,
    val totalFilesRead: Int = 0,
    val totalFilesModified: Int = 0,
    val totalSearchCount: Int = 0,
    val totalDiffCount: Int = 0,
    val totalCheckpointCount: Int = 0,
    val totalRollbackCount: Int = 0,
    val totalErrors: Int = 0,
    val totalRetries: Int = 0,
    val totalLatencyMs: Long = 0,
    val perModel: Map<String, ModelUsageSummary> = emptyMap(),
)

data class ModelUsageSummary(
    val modelId: String,
    val modelName: String,
    val requests: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
)
