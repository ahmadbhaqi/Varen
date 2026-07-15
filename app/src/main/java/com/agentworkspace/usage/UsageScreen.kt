package com.agentworkspace.usage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.ModelUsageSummary
import com.agentworkspace.data.model.UsageRecord
import com.agentworkspace.data.model.UsageSummary
import com.agentworkspace.shell.components.ProviderLogo
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.components.modern.EmptyStateModern
import com.agentworkspace.shell.components.modern.GlassCard
import com.agentworkspace.shell.components.modern.SectionHeader
import com.agentworkspace.shell.presentation.formatCompactCount
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption
import com.agentworkspace.shell.theme.MonoMetric
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsageScreen(
    onOpenDrawer: () -> Unit,
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val usageRecords by viewModel.usageRecords.collectAsStateWithLifecycle()
    var selectedPeriod by rememberSaveable { mutableStateOf(UsagePeriod.Today) }
    val dashboard = remember(usageRecords, selectedPeriod) {
        buildUsageDashboard(usageRecords, selectedPeriod)
    }

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                UsageHeader(
                    totalRecords = usageRecords.size,
                    onOpenDrawer = onOpenDrawer,
                )
            }
            item {
                UsagePeriodSelector(
                    selected = selectedPeriod,
                    onSelected = { selectedPeriod = it },
                )
            }

            when {
                usageRecords.isEmpty() -> item {
                    EmptyStateModern(
                        icon = Icons.Filled.Speed,
                        title = "No usage yet",
                        subtitle = "Usage appears after Varen completes an agent run.",
                        accent = AiTheme.colors.aiPrimary,
                    )
                }

                dashboard.records.isEmpty() -> item {
                    EmptyStateModern(
                        icon = Icons.Filled.Timeline,
                        title = "No activity for ${selectedPeriod.label}",
                        subtitle = "Choose another period to explore recorded usage.",
                        accent = AiTheme.colors.textSecondary,
                    )
                }

                else -> {
                    item { UsageHero(dashboard.summary, selectedPeriod) }
                    item { OverviewMetrics(dashboard.summary) }
                    item { UsageTrendPanel(dashboard.trend) }
                    item { ModelUsagePanel(dashboard.models) }
                    item { RecentRequestsPanel(dashboard.records.take(20)) }
                }
            }
        }
    }
}

@Composable
private fun UsageHeader(
    totalRecords: Int,
    onOpenDrawer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceIconButton(
            icon = Icons.Filled.Menu,
            contentDescription = "Open navigation",
            onClick = onOpenDrawer,
        )
        Spacer(Modifier.width(12.dp))
        SectionHeader(
            title = "Usage",
            subtitle = if (totalRecords == 0) "Local token analytics" else "$totalRecords recorded requests",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UsagePeriodSelector(
    selected: UsagePeriod,
    onSelected: (UsagePeriod) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AiTheme.colors.glassBase,
        border = BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UsagePeriod.entries.forEach { period ->
                val isSelected = period == selected
                Surface(
                    onClick = { onSelected(period) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AppTheme.colors.textPrimary else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = period.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) AppTheme.colors.textOnBrand else AppTheme.colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageHero(summary: UsageSummary, period: UsagePeriod) {
    val totalTokens = summary.totalInputTokens + summary.totalOutputTokens
    GlassCard(accentColor = AiTheme.colors.aiPrimary) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "TOTAL TOKENS",
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatCompactCount(totalTokens),
                    style = MonoMetric.copy(fontSize = 32.sp, lineHeight = 38.sp),
                    color = AiTheme.colors.textPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Input + output · ${period.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AiTheme.colors.textSecondary,
                )
            }
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = AppTheme.colors.accentSoft,
                border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
            ) {
                Text(
                    text = "LOCAL",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MonoCaption,
                    color = AiTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UsageSignal("Requests", formatCompactCount(summary.totalRequests), Modifier.weight(1f))
            UsageSignal("Runs", formatCompactCount(summary.totalExecutionCount), Modifier.weight(1f))
            UsageSignal("Tools", formatCompactCount(summary.totalToolCalls), Modifier.weight(1f))
        }
    }
}

@Composable
private fun UsageSignal(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colors.elevated)
            .border(BorderStroke(1.dp, AppTheme.colors.border), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(value, style = MonoMetric, color = AiTheme.colors.textPrimary, maxLines = 1)
        Text(label, style = MonoCaption, color = AiTheme.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun OverviewMetrics(summary: UsageSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UsageMetricCard(
                label = "Requests",
                value = formatCompactCount(summary.totalRequests),
                icon = Icons.Filled.RequestQuote,
                modifier = Modifier.weight(1f),
            )
            UsageMetricCard(
                label = "Input",
                value = formatCompactCount(summary.totalInputTokens),
                icon = Icons.Filled.Memory,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UsageMetricCard(
                label = "Cached",
                value = formatCompactCount(summary.totalCachedTokens),
                icon = Icons.Filled.Cached,
                modifier = Modifier.weight(1f),
            )
            UsageMetricCard(
                label = "Output",
                value = formatCompactCount(summary.totalOutputTokens),
                icon = Icons.Filled.Bolt,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun UsageMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, accentColor = AiTheme.colors.aiPrimary) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = value,
                    style = MonoMetric,
                    color = AiTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AiTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun UsageTrendPanel(points: List<UsageTrendPoint>) {
    GlassCard(accentColor = AiTheme.colors.aiPrimary) {
        AnalyticsSectionHeader(
            title = "Token activity",
            subtitle = "Input + output over time",
            icon = Icons.Filled.Timeline,
        )
        Spacer(Modifier.height(18.dp))
        UsageTrendChart(points)
    }
}

@Composable
private fun UsageTrendChart(points: List<UsageTrendPoint>) {
    val accent = AiTheme.colors.aiPrimary
    val grid = AppTheme.colors.borderStrong
    val maxTokens = points.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1) ?: 1

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp),
    ) {
        val chartBottom = size.height - 12.dp.toPx()
        val chartTop = 8.dp.toPx()
        val chartHeight = chartBottom - chartTop
        val lastIndex = (points.size - 1).coerceAtLeast(1)

        repeat(4) { index ->
            val y = chartTop + chartHeight * index / 3f
            drawLine(
                color = grid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (points.isEmpty()) return@Canvas

        val coordinates = points.mapIndexed { index, point ->
            val x = if (points.size == 1) size.width / 2f else size.width * index / lastIndex
            val y = chartBottom - (point.totalTokens.toFloat() / maxTokens) * chartHeight
            Offset(x, y)
        }
        val line = Path().apply {
            moveTo(coordinates.first().x, coordinates.first().y)
            coordinates.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            moveTo(coordinates.first().x, chartBottom)
            lineTo(coordinates.first().x, coordinates.first().y)
            coordinates.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(coordinates.last().x, chartBottom)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                startY = chartTop,
                endY = chartBottom,
            ),
        )
        drawPath(
            path = line,
            color = accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (coordinates.size <= 12) {
            coordinates.forEach { point -> drawCircle(accent, radius = 3.dp.toPx(), center = point) }
        }
    }

    if (points.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(points.first().label, style = MonoCaption, color = AiTheme.colors.textTertiary)
            if (points.size > 1) {
                Text(points.last().label, style = MonoCaption, color = AiTheme.colors.textTertiary)
            }
        }
    }
}

@Composable
private fun ModelUsagePanel(models: List<ModelUsageSummary>) {
    val totalTokens = models.sumOf { it.totalTokens }.coerceAtLeast(1)
    GlassCard(accentColor = AiTheme.colors.aiPrimary) {
        AnalyticsSectionHeader(
            title = "Usage by model",
            subtitle = "Highest token consumers",
            icon = Icons.Filled.DataUsage,
        )
        Spacer(Modifier.height(18.dp))
        models.take(6).forEachIndexed { index, model ->
            ModelUsageRow(
                model = model,
                share = model.totalTokens.toFloat() / totalTokens,
            )
            if (index != models.take(6).lastIndex) {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ModelUsageRow(model: ModelUsageSummary, share: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProviderLogo(providerName = model.modelId, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AiTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatCompactCount(model.totalTokens),
                    style = MonoCaption,
                    color = AiTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.elevated),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(AiTheme.colors.aiPrimary),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${model.requests} requests · ${model.inputTokens} in / ${model.outputTokens} out",
                style = MonoCaption,
                color = AiTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentRequestsPanel(records: List<UsageRecord>) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    GlassCard(accentColor = AiTheme.colors.aiPrimary) {
        AnalyticsSectionHeader(
            title = "Recent requests",
            subtitle = "Latest activity in this period",
            icon = Icons.Filled.Speed,
        )
        Spacer(Modifier.height(10.dp))
        records.forEachIndexed { index, record ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppTheme.colors.border),
                )
            }
            UsageRequestRow(
                record = record,
                formattedDate = dateFormat.format(Date(record.timestamp)),
            )
        }
    }
}

@Composable
private fun UsageRequestRow(record: UsageRecord, formattedDate: String) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (record.errors > 0) AiTheme.colors.textTertiary else AiTheme.colors.aiPrimary,
                ),
        )
        Spacer(Modifier.width(10.dp))
        ProviderLogo(
            providerName = record.modelId.orEmpty(),
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = record.modelId ?: "Unknown model",
                style = MaterialTheme.typography.bodyMedium,
                color = AiTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildUsageWorkSummary(record),
                style = MonoCaption,
                color = AiTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${formatCompactCount(record.inputTokens)}↑  ${formatCompactCount(record.outputTokens)}↓",
                style = MonoCaption,
                color = AiTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(formattedDate, style = MonoCaption, color = AiTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun AnalyticsSectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = AppTheme.colors.elevated,
            border = BorderStroke(1.dp, AppTheme.colors.border),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = AiTheme.colors.textPrimary,
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AiTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AiTheme.colors.textSecondary,
            )
        }
    }
}

private fun buildUsageWorkSummary(record: UsageRecord): String {
    val parts = mutableListOf<String>()
    if (record.toolCalls > 0) parts += "${record.toolCalls} tools"
    if (record.executionCount > 0) parts += "${record.executionCount} exec"
    if (record.filesRead > 0) parts += "${record.filesRead} read"
    if (record.filesModified > 0) parts += "${record.filesModified} modified"
    if (record.searchCount > 0) parts += "${record.searchCount} search"
    if (record.diffCount > 0) parts += "${record.diffCount} diffs"
    if (record.latencyMs > 0) parts += "${record.latencyMs}ms"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "Request recorded"
}
