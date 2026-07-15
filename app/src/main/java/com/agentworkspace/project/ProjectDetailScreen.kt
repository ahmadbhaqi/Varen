package com.agentworkspace.project

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import androidx.compose.ui.graphics.Brush
import com.agentworkspace.shell.components.WorkspaceHeader
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.components.WorkspaceTabBar
import com.agentworkspace.shell.components.WorkspaceDivider
import com.agentworkspace.shell.components.modern.GlassCard
import com.agentworkspace.shell.components.modern.StatusBadge
import com.agentworkspace.shell.components.modern.TaskCardModern
import com.agentworkspace.shell.components.modern.QuickActionButton
import com.agentworkspace.shell.components.modern.MetricCard
import com.agentworkspace.shell.presentation.formatCompactCount
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.MonoCaption
import com.agentworkspace.shell.components.bounceClick
import java.util.Locale

@Composable
fun ProjectDetailScreen(
    projectId: String,
    onTaskClick: (String) -> Unit,
    onFileClick: (String) -> Unit,
    onBack: () -> Unit,
    onStartTask: (taskId: String) -> Unit,
    onModelCatalog: () -> Unit,
    viewModel: ProjectViewModel = hiltViewModel(),
) {
    val projectFlow = remember(projectId) { viewModel.getProject(projectId) }
    val taskFlow = remember(projectId) { viewModel.getProjectTasks(projectId) }
    val fileFlow = remember(projectId) { viewModel.listProjectFiles(projectId) }
    val overviewFlow = remember(projectId) { viewModel.getProjectOverview(projectId) }
    val project by projectFlow.collectAsStateWithLifecycle(null)
    val tasks by taskFlow.collectAsStateWithLifecycle(emptyList())
    val files by fileFlow.collectAsStateWithLifecycle(emptyList())
    val overview by overviewFlow.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tasks", "Files", "Context", "Settings")

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WorkspaceHeader(
                    title = project?.name ?: "Project",
                    subtitle = project?.path ?: "Resolving workspace...",
                    leading = { WorkspaceIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) },
                ) {
                    WorkspaceIconButton(Icons.Filled.Tune, "Model settings", onModelCatalog, accent = AiTheme.colors.aiPrimary)
                }
                ReadinessPanel(project = project, overview = overview)
                WorkspaceTabBar(tabs = tabs, selectedIndex = selectedTab, onSelect = { selectedTab = it })
            }
            when (selectedTab) {
                0 -> TasksTab(
                    tasks = tasks,
                    overview = overview,
                    onTaskClick = onTaskClick,
                    onNewTask = { viewModel.createStarterTask(projectId) { onStartTask(it) } },
                    modifier = Modifier.weight(1f),
                )
                1 -> FilesTab(projectPath = project?.path, files = files, onFileClick = onFileClick, modifier = Modifier.weight(1f))
                2 -> ContextTab(project = project, overview = overview, modifier = Modifier.weight(1f))
                else -> SettingsTab(project = project, overview = overview, onModelCatalog = onModelCatalog, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReadinessPanel(project: Project?, overview: ProjectOverview) {
    GlassCard(accentColor = readinessColor(overview.readiness)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProjectMark()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(overview.readinessLabel, style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                Text(
                    project?.description?.takeIf { it.isNotBlank() } ?: "${overview.activeModel} via ${overview.activeConnection}",
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusDot(readinessColor(overview.readiness))
        }
    }
}

@Composable
private fun TasksTab(
    tasks: List<Task>,
    overview: ProjectOverview,
    onTaskClick: (String) -> Unit,
    onNewTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassCard(
                accentColor = AiTheme.colors.aiPrimary,
                onClick = onNewTask,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = AiTheme.colors.aiPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("New Task", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        Text("Open an agent task for this workspace", style = MonoCaption, color = AiTheme.colors.textSecondary)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    value = overview.activeTasks.toString(),
                    label = "Active",
                    icon = Icons.Filled.Speed,
                    accent = AiTheme.colors.aiPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    value = overview.completedTasks.toString(),
                    label = "Completed",
                    icon = Icons.Filled.TaskAlt,
                    accent = AiTheme.colors.aiSuccess,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    value = overview.diffCount.toString(),
                    label = "Diffs",
                    icon = Icons.Filled.Description,
                    accent = AiTheme.colors.aiWarning,
                    modifier = Modifier.weight(1f)
                )
                val formatCompactCount = { count: Long ->
                    if (count >= 1000000) String.format(Locale.US, "%.1fM", count / 1000000.0)
                    else if (count >= 1000) String.format(Locale.US, "%.1fK", count / 1000.0)
                    else count.toString()
                }
                MetricCard(
                    value = formatCompactCount(overview.totalTokens.toLong()),
                    label = "Tokens",
                    icon = Icons.Filled.Speed,
                    accent = AiTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (tasks.isEmpty()) {
            item {
                GlassCard(accentColor = AiTheme.colors.textTertiary) {
                    Text("No tasks yet", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("Create the first agent task for this project.", style = MonoCaption, color = AiTheme.colors.textSecondary)
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                TaskRow(task = task, onClick = { onTaskClick(task.id) })
            }
        }
    }
}

@Composable
private fun FilesTab(projectPath: String?, files: List<FileEntry>, onFileClick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Text(projectPath ?: "Resolving workspace path", style = MonoCaption, color = AiTheme.colors.textSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.Search, contentDescription = null, tint = AiTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        if (files.isEmpty()) {
            item {
                GlassCard(accentColor = AiTheme.colors.textTertiary) {
                    Text("No files visible", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("The workspace may be empty or waiting for folder permission.", style = MonoCaption, color = AiTheme.colors.textSecondary)
                }
            }
        } else {
            items(files.take(60), key = { it.uri }) { entry ->
                FileEntryRow(entry = entry, onClick = { if (!entry.isDirectory) onFileClick(entry.uri) })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 10.dp)) {
                Legend("M", "Modified", AiTheme.colors.aiWarning)
                Legend("A", "Added", AiTheme.colors.aiSuccess)
                Legend("D", "Deleted", AiTheme.colors.aiError)
            }
        }
    }
}

@Composable
private fun ContextTab(project: Project?, overview: ProjectOverview, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            GlassCard(accentColor = AiTheme.colors.aiPrimary) {
                Text(project?.description?.takeIf { it.isNotBlank() } ?: "Project context is built from task runs, file reads, diffs, approvals, and checkpoints.", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary)
            }
        }
        items(overview.recentHistory, key = { it.id }) { entry ->
            GlassCard(accentColor = AiTheme.colors.textSecondary) {
                Text(entry.type.displayName, style = MonoCaption, color = AiTheme.colors.aiSecondary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SettingsTab(project: Project?, overview: ProjectOverview, onModelCatalog: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SettingRow(Icons.Filled.Memory, "Active model", overview.activeModel, AiTheme.colors.aiPrimary, onModelCatalog) }
        item { SettingRow(Icons.Filled.Security, "Trust mode", project?.trustMode?.displayName ?: "Manual Approval", AiTheme.colors.aiSecondary, null) }
        item { SettingRow(Icons.Filled.Settings, "Provider", overview.activeConnection, AiTheme.colors.aiPrimary, onModelCatalog) }
        overview.runtimeNote?.let {
            item {
                GlassCard(accentColor = AiTheme.colors.aiWarning) {
                    Text("Runtime note", style = MonoCaption, color = AiTheme.colors.aiWarning, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, onClick: () -> Unit) {
    TaskCardModern(
        title = task.title.ifBlank { "Agent Task" },
        subtitle = task.goal.ifBlank { task.status.displayName },
        status = task.status.displayName,
        statusColor = taskStatusColor(task.status),
        icon = Icons.Filled.Description,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FileEntryRow(entry: FileEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (entry.isDirectory) Color.Transparent else AiTheme.colors.glassBase.copy(alpha = 0.3f))
            .bounceClick(enabled = !entry.isDirectory, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            tint = if (entry.isDirectory) AiTheme.colors.aiPrimary else AiTheme.colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodySmall,
            color = AiTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (entry.isDirectory) {
            Text(
                text = "${entry.childCount}",
                style = MonoCaption,
                color = AiTheme.colors.textTertiary
            )
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, color: Color, onClick: (() -> Unit)?) {
    GlassCard(accentColor = color, onClick = onClick) {
        MetricLine(icon, title, value, color)
    }
}

@Composable
private fun MetricLine(icon: ImageVector, label: String, value: String, color: Color) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProjectMark() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AiTheme.colors.glassBase)
            .border(1.dp, AiTheme.colors.surfaceBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = AiTheme.colors.aiPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun Legend(token: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(token, style = MonoCaption, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MonoCaption, color = AiTheme.colors.textSecondary)
    }
}

@Composable
private fun readinessColor(readiness: ReadinessState): Color = when (readiness) {
    ReadinessState.READY -> AiTheme.colors.aiSuccess
    ReadinessState.NEEDS_MODEL, ReadinessState.NEEDS_AUTH, ReadinessState.REGISTERED_ONLY -> AiTheme.colors.aiWarning
    ReadinessState.SETUP_REQUIRED -> AiTheme.colors.textTertiary
}

@Composable
private fun taskStatusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.COMPLETED -> AiTheme.colors.aiSuccess
    TaskStatus.FAILED -> AiTheme.colors.aiError
    TaskStatus.PAUSED, TaskStatus.QUEUED -> AiTheme.colors.textSecondary
    else -> AiTheme.colors.aiPrimary
}

private fun TaskStatus.isLive(): Boolean = this in setOf(
    TaskStatus.PLANNING,
    TaskStatus.READING_CONTEXT,
    TaskStatus.EDITING,
    TaskStatus.RUNNING_LOOP,
    TaskStatus.VERIFYING,
    TaskStatus.WAITING_APPROVAL,
    TaskStatus.EXECUTING,
    TaskStatus.RETRYING,
)
