package com.agentworkspace.usage

import com.agentworkspace.data.model.UsageRecord
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAnalyticsTest {
    private val zone = ZoneId.of("UTC")
    private val now = at(2026, 7, 15, 15, 0)

    @Test
    fun todayIncludesOnlyRecordsFromTheCurrentLocalDay() {
        val dashboard = buildUsageDashboard(
            records = listOf(
                usage(timestamp = at(2026, 7, 15, 0, 30), input = 120, output = 30),
                usage(timestamp = at(2026, 7, 14, 23, 59), input = 900, output = 100),
            ),
            period = UsagePeriod.Today,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(1, dashboard.records.size)
        assertEquals(150, dashboard.summary.totalInputTokens + dashboard.summary.totalOutputTokens)
    }

    @Test
    fun sevenDaysUsesSevenCalendarDaysIncludingToday() {
        val dashboard = buildUsageDashboard(
            records = listOf(
                usage(timestamp = at(2026, 7, 9, 0, 0), input = 10),
                usage(timestamp = at(2026, 7, 8, 23, 59), input = 99),
            ),
            period = UsagePeriod.Last7Days,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(listOf(10), dashboard.records.map { it.inputTokens })
    }

    @Test
    fun summaryAggregatesPersistedUsageSignals() {
        val dashboard = buildUsageDashboard(
            records = listOf(
                usage(
                    timestamp = at(2026, 7, 15, 10, 0),
                    requests = 2,
                    input = 100,
                    output = 40,
                    cached = 30,
                    reasoning = 12,
                    tools = 3,
                    executions = 1,
                    errors = 1,
                    latencyMs = 800,
                ),
                usage(
                    timestamp = at(2026, 7, 15, 11, 0),
                    requests = 1,
                    input = 50,
                    output = 20,
                    cached = 10,
                    reasoning = 4,
                    tools = 2,
                    executions = 2,
                    latencyMs = 200,
                ),
            ),
            period = UsagePeriod.All,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(3, dashboard.summary.totalRequests)
        assertEquals(150, dashboard.summary.totalInputTokens)
        assertEquals(60, dashboard.summary.totalOutputTokens)
        assertEquals(40, dashboard.summary.totalCachedTokens)
        assertEquals(16, dashboard.summary.totalReasoningTokens)
        assertEquals(5, dashboard.summary.totalToolCalls)
        assertEquals(3, dashboard.summary.totalExecutionCount)
        assertEquals(1, dashboard.summary.totalErrors)
        assertEquals(1_000L, dashboard.summary.totalLatencyMs)
    }

    @Test
    fun trendIsChronologicalAndCombinesRecordsByDay() {
        val dashboard = buildUsageDashboard(
            records = listOf(
                usage(timestamp = at(2026, 7, 15, 12, 0), input = 30, output = 20),
                usage(timestamp = at(2026, 7, 13, 12, 0), input = 10, output = 5),
                usage(timestamp = at(2026, 7, 15, 13, 0), input = 25, output = 25),
            ),
            period = UsagePeriod.Last7Days,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(listOf("Jul 13", "Jul 15"), dashboard.trend.map { it.label })
        assertEquals(listOf(15, 100), dashboard.trend.map { it.totalTokens })
    }

    @Test
    fun todayTrendUsesFourHourBucketsUpToTheCurrentTime() {
        val dashboard = buildUsageDashboard(
            records = listOf(
                usage(timestamp = at(2026, 7, 15, 1, 0), input = 10),
                usage(timestamp = at(2026, 7, 15, 5, 0), output = 20),
                usage(timestamp = at(2026, 7, 15, 13, 0), input = 30),
            ),
            period = UsagePeriod.Today,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(listOf("00:00", "04:00", "08:00", "12:00"), dashboard.trend.map { it.label })
        assertEquals(listOf(10, 20, 0, 30), dashboard.trend.map { it.totalTokens })
    }

    @Test
    fun modelsAreRankedByTokensAndMissingModelGetsReadableFallback() {
        val dashboard = buildUsageDashboard(
            records = listOf(
                usage(timestamp = now, modelId = "gpt-5", input = 20, output = 10),
                usage(timestamp = now, modelId = "claude-sonnet", input = 30, output = 20),
                usage(timestamp = now, modelId = null, input = 7, output = 3),
            ),
            period = UsagePeriod.All,
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(listOf("claude-sonnet", "gpt-5", "unknown"), dashboard.models.map { it.modelId })
        assertEquals(listOf("claude-sonnet", "gpt-5", "Unknown model"), dashboard.models.map { it.modelName })
        assertEquals(listOf(50, 30, 10), dashboard.models.map { it.totalTokens })
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun usage(
        timestamp: Long,
        modelId: String? = "gpt-5",
        requests: Int = 1,
        input: Int = 0,
        output: Int = 0,
        cached: Int = 0,
        reasoning: Int = 0,
        tools: Int = 0,
        executions: Int = 0,
        errors: Int = 0,
        latencyMs: Long = 0,
    ) = UsageRecord(
        timestamp = timestamp,
        modelId = modelId,
        requests = requests,
        inputTokens = input,
        outputTokens = output,
        cachedTokens = cached,
        reasoningTokens = reasoning,
        toolCalls = tools,
        executionCount = executions,
        errors = errors,
        latencyMs = latencyMs,
    )
}
