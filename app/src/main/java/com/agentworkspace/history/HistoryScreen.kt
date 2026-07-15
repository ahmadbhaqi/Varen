package com.agentworkspace.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.HistoryType
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.components.modern.GlassCard
import com.agentworkspace.shell.components.bounceClick
import com.agentworkspace.shell.presentation.historyFilterLabels
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.MonoCaption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(
    onHistoryItemClick: (HistoryEntry) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = historyFilterLabels()
    val filtered = remember(history, selectedTab) {
        history.filter { entry ->
            when (selectedTab) {
                1 -> entry.type in setOf(HistoryType.TASK_STARTED, HistoryType.TASK_COMPLETED, HistoryType.TASK_FAILED)
                2 -> entry.type in setOf(HistoryType.FILE_WRITE, HistoryType.CHECKPOINT_CREATED, HistoryType.ROLLBACK)
                3 -> entry.type in setOf(HistoryType.TOOL_CALL, HistoryType.COMMAND_EXECUTION, HistoryType.CONNECTION_EVENT, HistoryType.ERROR)
                else -> true
            }
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { dayLabel(it.timestamp) } }

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HistoryHeader(onBack = onBack)
            }
            item {
                HistoryFilterRow(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }
            if (filtered.isEmpty()) {
                item {
                    GlassCard(accentColor = AiTheme.colors.textSecondary) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("No history yet", style = MaterialTheme.typography.titleMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("Completed tasks, changes, and agent events will appear here.", style = MonoCaption, color = AiTheme.colors.textSecondary)
                        }
                    }
                }
            } else {
                grouped.forEach { (label, entries) ->
                    item {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = AiTheme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                        )
                    }
                    items(
                        items = entries,
                        key = { entry -> entry.id },
                        contentType = { "history-entry" },
                    ) { entry ->
                        GlassCard(accentColor = historyAccent(entry)) {
                            HistoryTimelineRow(
                                entry = entry,
                                onClick = if (entry.projectId != null && entry.taskId != null) {
                                    { onHistoryItemClick(entry) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Project History",
            style = MaterialTheme.typography.titleLarge,
            color = AiTheme.colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        onBack?.let {
            WorkspaceIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = it,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
fun HistoryFilterRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AiTheme.colors.glassBase)
            .border(1.dp, AiTheme.colors.surfaceBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            val contentColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else AiTheme.colors.textSecondary,
                label = "textColor"
            )
            val bgContainerColor by animateColorAsState(
                targetValue = if (selected) AiTheme.colors.aiPrimary else Color.Transparent,
                label = "bgColor"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgContainerColor)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onSelect(idx) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun HistoryTimelineRow(entry: HistoryEntry, onClick: (() -> Unit)?) {
    val accent = historyAccent(entry)
    val rowModifier = if (onClick != null) {
        Modifier.bounceClick(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(historyIcon(entry), contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.description.removePrefix("Checkpoint created: "),
                style = MaterialTheme.typography.bodyMedium,
                color = AiTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (entry.success) AiTheme.colors.aiSuccess else AiTheme.colors.aiError)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (entry.success) "Completed" else "Failed",
                    style = MonoCaption,
                    color = if (entry.success) AiTheme.colors.aiSuccess else AiTheme.colors.aiError,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(timeLabel(entry.timestamp), style = MonoCaption, color = AiTheme.colors.textSecondary)
        if (onClick != null) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AiTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun historyAccent(entry: HistoryEntry): Color = when {
    !entry.success -> AiTheme.colors.aiError
    entry.type == HistoryType.TASK_COMPLETED -> AiTheme.colors.aiSuccess
    entry.type == HistoryType.ROLLBACK -> AiTheme.colors.aiWarning
    entry.type == HistoryType.FILE_WRITE -> AiTheme.colors.aiPrimary
    entry.type == HistoryType.CHECKPOINT_CREATED -> AiTheme.colors.aiPrimary
    else -> AiTheme.colors.aiSecondary
}

private fun historyIcon(entry: HistoryEntry): ImageVector = when {
    !entry.success -> Icons.Filled.Warning
    entry.description.contains("checkout", ignoreCase = true) -> Icons.Filled.BugReport
    entry.description.contains("dark mode", ignoreCase = true) -> Icons.Filled.DarkMode
    entry.description.contains("image", ignoreCase = true) -> Icons.Filled.AutoAwesome
    entry.description.contains("dependency", ignoreCase = true) -> Icons.Filled.SystemUpdateAlt
    entry.description.contains("test", ignoreCase = true) -> Icons.Filled.Science
    entry.type == HistoryType.CHECKPOINT_CREATED -> Icons.Filled.Folder
    entry.type == HistoryType.FILE_WRITE -> Icons.Filled.Code
    else -> Icons.Filled.History
}

private fun dayLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val ageDays = TimeUnit.MILLISECONDS.toDays(now - timestamp)
    return when (ageDays) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun timeLabel(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
