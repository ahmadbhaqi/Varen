package com.agentworkspace.usage

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.UsageRecord
import com.agentworkspace.data.model.UsageSummary
import com.agentworkspace.shell.components.ProviderLogo
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.components.modern.EmptyStateModern
import com.agentworkspace.shell.components.modern.GlassCard
import com.agentworkspace.shell.components.modern.MetricCard
import com.agentworkspace.shell.components.modern.SectionHeader
import com.agentworkspace.shell.presentation.formatCompactCount
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsageScreen(
    onOpenDrawer: () -> Unit,
    viewModel: UsageViewModel = hiltViewModel(),
) {
    val totalUsage by viewModel.totalUsage.collectAsStateWithLifecycle(null)
    val recentUsage by viewModel.recentUsage.collectAsStateWithLifecycle(emptyList())

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WorkspaceIconButton(
                        icon = Icons.Filled.Menu,
                        contentDescription = "Open navigation",
                        onClick = onOpenDrawer,
                    )
                    Spacer(Modifier.width(12.dp))
                    SectionHeader(
                        title = "Usage",
                        subtitle = totalUsage?.let { "${formatCompactCount(it.totalInputTokens + it.totalOutputTokens)} tokens" } ?: "No usage recorded",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                totalUsage?.let { UsageSummaryPanel(it) } ?: EmptyStateModern(
                    icon = Icons.Filled.Speed,
                    title = "No usage yet",
                    subtitle = "Usage appears after agent runs.",
                    accent = AiTheme.colors.aiPrimary,
                )
            }
            item {
                Text(
                    "Recent usage",
                    style = MaterialTheme.typography.labelMedium,
                    color = AiTheme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            if (recentUsage.isEmpty()) {
                item {
                    EmptyStateModern(
                        icon = Icons.Filled.Speed,
                        title = "No recent activity",
                        subtitle = "Detailed run history will be displayed here.",
                        accent = AiTheme.colors.textTertiary,
                    )
                }
            } else {
                items(recentUsage, key = { it.id }) { record ->
                    UsageRecordItem(record)
                }
            }
        }
    }
}

@Composable
private fun UsageSummaryPanel(summary: UsageSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "Input",
                value = formatCompactCount(summary.totalInputTokens),
                icon = Icons.Filled.Memory,
                accent = AiTheme.colors.aiPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Output",
                value = formatCompactCount(summary.totalOutputTokens),
                icon = Icons.Filled.Memory,
                accent = AiTheme.colors.aiSecondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "Cached",
                value = formatCompactCount(summary.totalCachedTokens),
                icon = Icons.Filled.Bolt,
                accent = AiTheme.colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Runs",
                value = formatCompactCount(summary.totalExecutionCount),
                icon = Icons.Filled.RequestQuote,
                accent = AiTheme.colors.aiSuccess,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "Tools",
                value = formatCompactCount(summary.totalToolCalls),
                icon = Icons.Filled.Speed,
                accent = AiTheme.colors.aiWarning,
                modifier = Modifier.weight(0.5f)
            )
            Spacer(Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun UsageRecordItem(record: UsageRecord) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    GlassCard(
        accentColor = AiTheme.colors.aiPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderLogo(
                providerName = record.modelId ?: "",
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.modelId ?: "Unknown model",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AiTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(dateFormat.format(Date(record.timestamp)), style = MonoCaption, color = AiTheme.colors.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text(buildUsageWorkSummary(record), style = MonoCaption, color = AiTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCompactCount(record.inputTokens + record.outputTokens), style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.aiPrimary, fontWeight = FontWeight.SemiBold)
                Text("${record.requests} req", style = MonoCaption, color = AiTheme.colors.textSecondary)
            }
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
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "request recorded"
}
