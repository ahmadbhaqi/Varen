package com.agentworkspace.usage

import com.agentworkspace.data.model.ModelUsageSummary
import com.agentworkspace.data.model.UsageRecord
import com.agentworkspace.data.model.UsageSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class UsagePeriod(val label: String) {
    Today("Today"),
    Last7Days("7D"),
    Last30Days("30D"),
    All("Recent"),
}

data class UsageTrendPoint(
    val timestamp: Long,
    val label: String,
    val inputTokens: Int,
    val outputTokens: Int,
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

data class UsageDashboardData(
    val period: UsagePeriod,
    val records: List<UsageRecord>,
    val summary: UsageSummary,
    val trend: List<UsageTrendPoint>,
    val models: List<ModelUsageSummary>,
)

fun buildUsageDashboard(
    records: List<UsageRecord>,
    period: UsagePeriod,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): UsageDashboardData {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val firstDate = when (period) {
        UsagePeriod.Today -> now.toLocalDate()
        UsagePeriod.Last7Days -> now.toLocalDate().minusDays(6)
        UsagePeriod.Last30Days -> now.toLocalDate().minusDays(29)
        UsagePeriod.All -> null
    }
    val firstTimestamp = firstDate?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli()
    val filtered = records
        .asSequence()
        .filter { it.timestamp <= nowMillis }
        .filter { firstTimestamp == null || it.timestamp >= firstTimestamp }
        .sortedByDescending { it.timestamp }
        .toList()

    val models = filtered
        .groupBy { it.modelId?.takeIf(String::isNotBlank) ?: UNKNOWN_MODEL_ID }
        .map { (modelId, modelRecords) ->
            val input = modelRecords.sumOf { it.inputTokens }
            val output = modelRecords.sumOf { it.outputTokens }
            ModelUsageSummary(
                modelId = modelId,
                modelName = if (modelId == UNKNOWN_MODEL_ID) "Unknown model" else modelId,
                requests = modelRecords.sumOf { it.requests },
                inputTokens = input,
                outputTokens = output,
                totalTokens = input + output,
            )
        }
        .sortedWith(compareByDescending<ModelUsageSummary> { it.totalTokens }.thenBy { it.modelName })

    return UsageDashboardData(
        period = period,
        records = filtered,
        summary = filtered.toSummary(models),
        trend = filtered.toTrend(period, nowMillis, zoneId),
        models = models,
    )
}

private fun List<UsageRecord>.toSummary(models: List<ModelUsageSummary>) = UsageSummary(
    totalRequests = sumOf { it.requests },
    totalInputTokens = sumOf { it.inputTokens },
    totalOutputTokens = sumOf { it.outputTokens },
    totalCachedTokens = sumOf { it.cachedTokens },
    totalReasoningTokens = sumOf { it.reasoningTokens },
    totalToolCalls = sumOf { it.toolCalls },
    totalExecutionCount = sumOf { it.executionCount },
    totalFilesRead = sumOf { it.filesRead },
    totalFilesModified = sumOf { it.filesModified },
    totalSearchCount = sumOf { it.searchCount },
    totalDiffCount = sumOf { it.diffCount },
    totalCheckpointCount = sumOf { it.checkpointCount },
    totalRollbackCount = sumOf { it.rollbackCount },
    totalErrors = sumOf { it.errors },
    totalRetries = sumOf { it.retries },
    totalLatencyMs = sumOf { it.latencyMs },
    perModel = models.associateBy { it.modelId },
)

private fun List<UsageRecord>.toTrend(
    period: UsagePeriod,
    nowMillis: Long,
    zoneId: ZoneId,
): List<UsageTrendPoint> {
    if (period == UsagePeriod.Today) {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val dayStart = now.toLocalDate().atStartOfDay(zoneId)
        val grouped = groupBy { record ->
            Instant.ofEpochMilli(record.timestamp).atZone(zoneId).hour / HOURS_PER_BUCKET
        }
        return (0..now.hour / HOURS_PER_BUCKET).map { bucket ->
            val bucketRecords = grouped[bucket].orEmpty()
            val bucketStart = dayStart.plusHours(bucket.toLong() * HOURS_PER_BUCKET)
            UsageTrendPoint(
                timestamp = bucketStart.toInstant().toEpochMilli(),
                label = "%02d:00".format(Locale.ENGLISH, bucket * HOURS_PER_BUCKET),
                inputTokens = bucketRecords.sumOf { it.inputTokens },
                outputTokens = bucketRecords.sumOf { it.outputTokens },
            )
        }
    }

    val labelFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    return groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }
        .toSortedMap()
        .map { (date, records) ->
            UsageTrendPoint(
                timestamp = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                label = date.format(labelFormatter),
                inputTokens = records.sumOf { it.inputTokens },
                outputTokens = records.sumOf { it.outputTokens },
            )
        }
}

private const val UNKNOWN_MODEL_ID = "unknown"
private const val HOURS_PER_BUCKET = 4
