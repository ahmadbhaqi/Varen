package com.agentworkspace.checkpoint

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.Checkpoint
import com.agentworkspace.data.model.CheckpointFile
import com.agentworkspace.data.model.CheckpointScope
import com.agentworkspace.shell.components.WorkspaceClickablePanel
import com.agentworkspace.shell.components.WorkspaceDivider
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.WorkspacePanel
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.components.WorkspaceSearchField
import com.agentworkspace.shell.presentation.checkpointTotals
import com.agentworkspace.shell.presentation.formatCheckpointSizeBytes
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun CheckpointsScreen(
    onBack: () -> Unit,
    onCheckpointClick: (String) -> Unit,
    viewModel: CheckpointsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val filtered = remember(uiState.checkpoints, query) {
        uiState.checkpoints.filter {
            query.isBlank() ||
                it.reason.contains(query, ignoreCase = true) ||
                it.files.any { file -> file.path.contains(query, ignoreCase = true) }
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { dayLabel(it.createdAt) } }
    val totals = checkpointTotals(uiState.checkpoints)

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    ScreenHeader(title = "Checkpoints", onBack = onBack, close = true)
                }
                item {
                    WorkspaceSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search checkpoints...",
                    )
                }
                item {
                    CheckpointStats(total = totals.total, automatic = totals.automatic, manual = totals.manual)
                }
                if (filtered.isEmpty()) {
                    item {
                        WorkspacePanel {
                            Text("No checkpoints yet", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                uiState.projectName?.let { "Checkpoints for $it will appear here." } ?: "Add a project to start creating checkpoints.",
                                style = MonoCaption,
                                color = AppTheme.colors.textSecondary,
                            )
                        }
                    }
                } else {
                    grouped.forEach { (label, checkpoints) ->
                        item {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                        checkpoints.forEach { checkpoint ->
                            item(key = checkpoint.id) {
                                CheckpointCard(
                                    checkpoint = checkpoint,
                                    onClick = { onCheckpointClick(checkpoint.id) },
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun CheckpointDetailScreen(
    onBack: () -> Unit,
    viewModel: CheckpointsViewModel = hiltViewModel(),
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val checkpoint = state.checkpoint

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    ScreenHeader(title = "Checkpoint Details", onBack = onBack, close = false)
                }
                if (checkpoint == null) {
                    item {
                        WorkspacePanel {
                            Text("Checkpoint not found", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                            Text("The checkpoint may have been removed.", style = MonoCaption, color = AppTheme.colors.textSecondary)
                        }
                    }
                } else {
                    item { CheckpointHero(checkpoint) }
                    item {
                        Text("Summary", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    item { SummaryPanel(checkpoint) }
                    item {
                        Text("Changes", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    item { ChangesPanel(checkpoint) }
                    item {
                        Text("Files included", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    item { FilesPanel(checkpoint.files) }
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, close: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!close) {
            WorkspaceIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        if (close) {
            WorkspaceIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onBack,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun CheckpointStats(total: Int, automatic: Int, manual: Int) {
    WorkspacePanel(contentPadding = PaddingValues(horizontal = 0.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell(total.toString(), "Total", Modifier.weight(1f))
            VerticalDivider()
            StatCell(automatic.toString(), "Automatic", Modifier.weight(1f))
            VerticalDivider()
            StatCell(manual.toString(), "Manual", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MonoCaption, color = AppTheme.colors.textSecondary)
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(48.dp)
            .background(AppTheme.colors.border.copy(alpha = 0.5f)),
    )
}

@Composable
private fun CheckpointCard(checkpoint: Checkpoint, onClick: () -> Unit) {
    WorkspaceClickablePanel(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        checkpoint.reason.ifBlank { "Checkpoint" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TypePill(checkpoint)
                    Spacer(Modifier.width(8.dp))
                    Text(timeLabel(checkpoint.createdAt), style = MonoCaption, color = AppTheme.colors.textSecondary)
                }
                Text(
                    checkpointDescription(checkpoint),
                    style = MonoCaption,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MetaChip(Icons.Filled.Description, "${checkpoint.files.size} files")
                    MetaChip(Icons.Filled.Storage, checkpointSize(checkpoint))
                }
            }
        }
    }
}

@Composable
private fun CheckpointHero(checkpoint: Checkpoint) {
    WorkspacePanel(selected = true, accent = AppTheme.colors.accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = AppTheme.colors.accentSoft) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.BookmarkBorder, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(checkpoint.reason.ifBlank { "Checkpoint" }, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                Text(if (checkpoint.isTrusted) "Manual checkpoint" else "Auto checkpoint", style = MonoCaption, color = AppTheme.colors.textSecondary)
                Text("${dayLabel(checkpoint.createdAt)}, ${timeLabel(checkpoint.createdAt)}", style = MonoCaption, color = AppTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SummaryPanel(checkpoint: Checkpoint) {
    WorkspacePanel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        SummaryRow(Icons.Filled.Description, "Files", "${checkpoint.files.size} files")
        WorkspaceDivider()
        SummaryRow(Icons.Filled.Storage, "Size", checkpointSize(checkpoint))
        WorkspaceDivider()
        SummaryRow(Icons.Filled.Schedule, "Created", "${dayLabel(checkpoint.createdAt)}, ${timeLabel(checkpoint.createdAt)}")
        WorkspaceDivider()
        SummaryRow(Icons.Filled.BookmarkBorder, "Type", if (checkpoint.isTrusted) "Manual" else "Automatic")
        WorkspaceDivider()
        SummaryRow(Icons.Filled.Description, "Description", checkpointDescription(checkpoint))
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChangesPanel(checkpoint: Checkpoint) {
    val total = checkpoint.files.size
    WorkspacePanel(contentPadding = PaddingValues(horizontal = 0.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ChangeMetric(total.toString(), "Files", AppTheme.colors.textPrimary, Modifier.weight(1f))
            ChangeMetric(checkpoint.scope.displayName, "Scope", AppTheme.colors.accent, Modifier.weight(1f))
            ChangeMetric(checkpointSize(checkpoint), "Snapshot", AppTheme.colors.success, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChangeMetric(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MonoCaption, color = AppTheme.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun FilesPanel(files: List<CheckpointFile>) {
    WorkspacePanel(contentPadding = PaddingValues(12.dp)) {
        files.take(4).forEachIndexed { index, file ->
            FileLine(file)
            if (index != files.take(4).lastIndex) Spacer(Modifier.height(10.dp))
        }
        if (files.size > 4) {
            Spacer(Modifier.height(10.dp))
            Text("+${files.size - 4} more files", style = MonoCaption, color = AppTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun FileLine(file: CheckpointFile) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (file.path.endsWith("/")) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            file.path,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = AppTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TypePill(checkpoint: Checkpoint) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            if (checkpoint.isTrusted) "Manual" else "Auto",
            style = MonoCaption,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MonoCaption, color = AppTheme.colors.textSecondary)
    }
}

private fun checkpointDescription(checkpoint: Checkpoint): String = when (checkpoint.scope) {
    CheckpointScope.SINGLE_FILE -> "Created before modifying a file"
    CheckpointScope.MULTI_FILE -> "Created before modifying multiple files"
    CheckpointScope.PROJECT_WIDE -> "Created before project-wide changes"
}

private fun checkpointSize(checkpoint: Checkpoint): String =
    formatCheckpointSizeBytes(checkpoint.files.sumOf { it.content.toByteArray().size })

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
