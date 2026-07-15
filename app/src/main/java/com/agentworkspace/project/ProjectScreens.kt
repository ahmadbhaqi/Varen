package com.agentworkspace.project

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore

import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.github.isGitHubProjectPath
import com.agentworkspace.shell.components.CapabilityChip
import com.agentworkspace.shell.components.EmptyStatePanel
import com.agentworkspace.shell.components.IconTile
import com.agentworkspace.shell.components.StatusPill
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.MonoCaption
import com.agentworkspace.shell.components.modern.GlassCard
import com.agentworkspace.shell.components.WorkspaceScreenBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyProjectDetailScreen(
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
    val fileEntries by fileFlow.collectAsStateWithLifecycle(emptyList())
    val overview by overviewFlow.collectAsStateWithLifecycle()
    val isRemoteProject = project?.path?.let { isGitHubProjectPath(it) } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        project?.name ?: "Project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onModelCatalog) {
                        Icon(Icons.Filled.Settings, contentDescription = "Model & Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.createStarterTask(projectId) { taskId -> onStartTask(taskId) }
                },
                containerColor = AppTheme.colors.accent,
                contentColor = AppTheme.colors.textOnBrand,
                shape = MaterialTheme.shapes.medium,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New task") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            project?.let { proj ->
                item {
                    ProjectInfoCard(
                        project = proj,
                        onTrustModeChange = { mode ->
                            viewModel.updateTrustMode(projectId, mode)
                        },
                    )
                }
            }

            item {
                ProjectCommandCenter(
                    overview = overview,
                    onModelCatalog = onModelCatalog,
                )
            }

            item { SectionHeader(title = "Files", count = fileEntries.size) }
            if (fileEntries.isEmpty()) {
                item { EmptyFilesState() }
            } else {
                items(fileEntries.take(12)) { entry ->
                    FileItemCard(
                        entry = entry,
                        onClick = {
                            if (!isRemoteProject && !entry.isDirectory) {
                                onFileClick(entry.uri)
                            }
                        },
                    )
                }
                if (fileEntries.size > 12) {
                    item {
                        Text(
                            "${fileEntries.size - 12} more items hidden for mobile focus",
                            style = MonoCaption,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            item { SectionHeader(title = "Tasks", count = tasks.size) }

            if (tasks.isEmpty()) {
                item { EmptyTasksState() }
            } else {
                items(tasks) { task ->
                    TaskItemCard(
                        task = task,
                        onClick = { onTaskClick(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectInfoCard(
    project: Project,
    onTrustModeChange: (TrustMode) -> Unit,
) {
    var showTrustDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.44f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        project.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        project.path,
                        style = MonoCaption,
                        color = AppTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(
                    text = if (isGitHubProjectPath(project.path)) "GitHub" else "Device",
                    color = if (isGitHubProjectPath(project.path)) MaterialTheme.colorScheme.secondary else AppTheme.colors.accent,
                )
            }

            if (project.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "Trust mode",
                style = MonoCaption,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTrustDialog = true }
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            project.trustMode.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textPrimary,
                            maxLines = 1,
                        )
                        Text(
                            project.trustMode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Change",
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showTrustDialog) {
        TrustModeDialog(
            currentMode = project.trustMode,
            onModeSelected = { mode ->
                onTrustModeChange(mode)
                showTrustDialog = false
            },
            onDismiss = { showTrustDialog = false },
        )
    }
}

@Composable
private fun ProjectCommandCenter(
    overview: ProjectOverview,
    onModelCatalog: () -> Unit,
) {
    val readinessColor = when (overview.readiness) {
        ReadinessState.READY -> AppTheme.colors.success
        ReadinessState.NEEDS_MODEL -> AppTheme.colors.warning
        ReadinessState.NEEDS_AUTH -> AppTheme.colors.warning
        ReadinessState.REGISTERED_ONLY -> AppTheme.colors.info
        ReadinessState.SETUP_REQUIRED -> AppTheme.colors.error
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.44f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(readinessColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = readinessColor, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Agent",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${overview.activeModel} via ${overview.activeConnection}",
                        style = MonoCaption,
                        color = AppTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(text = overview.readinessLabel, color = readinessColor, pulse = overview.readiness == ReadinessState.READY)
            }

            overview.runtimeNote?.let { note ->
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(readinessColor.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = readinessColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OverviewSignal("Tokens", formatCompact(overview.totalTokens), Icons.Filled.Bolt, AppTheme.colors.info, Modifier.weight(1f))
                OverviewSignal("Review", overview.pendingReview.toString(), Icons.Filled.Security, AppTheme.colors.warning, Modifier.weight(1f))
                OverviewSignal("Diffs", overview.diffCount.toString(), Icons.Filled.Description, AppTheme.colors.accent, Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OverviewSignal("Active", overview.activeTasks.toString(), Icons.Filled.Schedule, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                OverviewSignal("Done", overview.completedTasks.toString(), Icons.Filled.CheckCircle, AppTheme.colors.success, Modifier.weight(1f))
                OverviewSignal("Checkpoints", overview.checkpointCount.toString(), Icons.Filled.Bookmark, AppTheme.colors.warning, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onModelCatalog) {
                    Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Model")
                }
                Spacer(Modifier.weight(1f))
                Text("${overview.totalRequests} requests", style = MonoCaption, color = AppTheme.colors.textSecondary)
            }

            if (overview.recentHistory.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Recent activity", style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                overview.recentHistory.forEach { entry ->
                    ActivityLine(entry)
                }
            }
        }
    }
}

@Composable
private fun OverviewSignal(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            Text(label, style = MonoCaption, color = AppTheme.colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun ActivityLine(entry: HistoryEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(if (entry.success) AppTheme.colors.success else AppTheme.colors.error),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.type.displayName, style = MonoCaption, color = AppTheme.colors.textSecondary)
            Text(entry.description, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textPrimary, maxLines = 2)
        }
    }
}

@Composable
private fun TrustModeDialog(
    currentMode: TrustMode,
    onModeSelected: (TrustMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trust Mode") },
        text = {
            Column {
                TrustMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AppTheme.colors.accent,
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                mode.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = AppTheme.colors.textPrimary,
                            )
                            Text(
                                mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun TaskItemCard(
    task: Task,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = AppTheme.colors.surfaceVariant.copy(alpha = 0.72f),
        border = androidx.compose.foundation.BorderStroke(1.dp, task.status.color().copy(alpha = 0.26f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = if (task.status == TaskStatus.COMPLETED) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                accent = task.status.color(),
                size = 40.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    task.goal.ifBlank { "Project task" },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(text = task.status.displayName, color = task.status.color(), pulse = task.status.isLive())
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}


@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.colors.textPrimary,
        )
        Text(
            "$count total",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun FileItemCard(entry: FileEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.44f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                accent = if (entry.isDirectory) AppTheme.colors.accent else AppTheme.colors.textSecondary,
                size = 34.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (entry.isDirectory) "${entry.childCount} items" else "Open file",
                    style = MonoCaption,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
            if (entry.isDirectory) {
                CapabilityChip(text = "Folder", color = AppTheme.colors.accent)
            }
        }
    }
}

@Composable
private fun EmptyFilesState() {
    EmptyStatePanel(
        icon = Icons.Filled.Folder,
        title = "No readable files yet",
        subtitle = "Choose a workspace folder with files so the agent can gather context.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        accent = AppTheme.colors.accent,
    )
}

@Composable
private fun EmptyTasksState() {
    EmptyStatePanel(
        icon = Icons.Filled.AutoAwesome,
        title = "No tasks yet",
        subtitle = "Create a task to let the agent plan, edit, verify, and report back inside this project.",
        modifier = Modifier
            .fillMaxWidth()
    )
}



private enum class ProjectSourceMode(val label: String) {
    DEVICE("Device"),
    GITHUB("GitHub"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ProjectViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var sourceMode by remember { mutableStateOf(ProjectSourceMode.DEVICE) }
    var name by remember { mutableStateOf("") }
    var workspaceUri by remember { mutableStateOf<Uri?>(null) }
    var description by remember { mutableStateOf("") }
    var selectedTrustMode by remember { mutableStateOf(TrustMode.MANUAL) }
    var selectedFolderLabel by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    val hasSavedGitHubToken = remember { viewModel.hasSavedGitHubToken() }

    val createState by viewModel.createState.collectAsStateWithLifecycle()
    val openWorkspaceTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val requestedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val takeFlags = ((result.data?.flags ?: requestedFlags) and requestedFlags)
            .takeIf { it != 0 }
            ?: requestedFlags
        runCatching { context.contentResolver.takePersistableUriPermission(uri, takeFlags) }

        workspaceUri = uri
        selectedFolderLabel = DocumentFile.fromTreeUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast(':')
            ?: "Selected workspace"
        if (sourceMode == ProjectSourceMode.DEVICE && name.isBlank()) {
            name = selectedFolderLabel
        }
    }

    fun launchFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        openWorkspaceTree.launch(intent)
    }

    LaunchedEffect(createState) {
        if (createState is ProjectViewModel.CreateProjectState.Success) {
            onCreated((createState as ProjectViewModel.CreateProjectState.Success).projectId)
            viewModel.resetCreateState()
        }
    }

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        val selectedUri = workspaceUri
        val isLoading = createState is ProjectViewModel.CreateProjectState.Loading
        val canSubmit = !isLoading && when (sourceMode) {
            ProjectSourceMode.DEVICE -> selectedUri != null && name.isNotBlank()
            ProjectSourceMode.GITHUB -> repoUrl.isNotBlank() && (githubToken.isNotBlank() || hasSavedGitHubToken)
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (sourceMode == ProjectSourceMode.GITHUB) "Connect GitHub" else "New Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AiTheme.colors.textPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = AiTheme.colors.textPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            ) {
                item {
                    Text(
                        "Where should the agent work?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AiTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SourceOptionCard(
                            selected = sourceMode == ProjectSourceMode.DEVICE,
                            title = "Device",
                            subtitle = selectedFolderLabel.ifBlank { "System folder picker" },
                            icon = Icons.Filled.Folder,
                            onClick = {
                                sourceMode = ProjectSourceMode.DEVICE
                                viewModel.resetCreateState()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SourceOptionCard(
                            selected = sourceMode == ProjectSourceMode.GITHUB,
                            title = "GitHub",
                            subtitle = "Remote repo branch",
                            icon = Icons.Filled.Cloud,
                            onClick = {
                                sourceMode = ProjectSourceMode.GITHUB
                                viewModel.resetCreateState()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (sourceMode == ProjectSourceMode.DEVICE) {
                    item {
                        GlassCard(accentColor = AiTheme.colors.aiPrimary) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AiTheme.colors.aiPrimary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Folder, contentDescription = null, tint = AiTheme.colors.aiPrimary)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            selectedFolderLabel.ifBlank { "No folder selected" },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = AiTheme.colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            if (selectedUri == null) "Use the Android folder picker" else "Permission saved for this workspace",
                                            style = MonoCaption,
                                            color = AiTheme.colors.textSecondary,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = ::launchFolderPicker,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AiTheme.colors.aiPrimary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (selectedUri == null) "Choose folder" else "Change folder")
                                }
                            }
                        }
                    }
                } else {
                    item {
                        GlassCard(accentColor = AiTheme.colors.aiPrimary) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedTextField(
                                    value = repoUrl,
                                    onValueChange = { repoUrl = it },
                                    label = { Text("Repository", color = AiTheme.colors.textSecondary) },
                                    placeholder = { Text("owner/repo") },
                                    leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null, tint = AiTheme.colors.aiPrimary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = workspaceTextFieldColors(),
                                )
                                OutlinedTextField(
                                    value = branch,
                                    onValueChange = { branch = it },
                                    label = { Text("Branch", color = AiTheme.colors.textSecondary) },
                                    placeholder = { Text("Default branch") },
                                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = AiTheme.colors.aiPrimary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = workspaceTextFieldColors(),
                                )
                                OutlinedTextField(
                                    value = githubToken,
                                    onValueChange = { githubToken = it },
                                    label = {
                                        Text(if (hasSavedGitHubToken) "GitHub token" else "GitHub token required", color = AiTheme.colors.textSecondary)
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Security, contentDescription = null, tint = AiTheme.colors.aiPrimary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = workspaceTextFieldColors(),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AiTheme.colors.aiPrimary.copy(alpha = 0.08f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Security, contentDescription = null, tint = AiTheme.colors.aiPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Stored in Android Keystore. Needs repo contents plus actions/workflows access.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AiTheme.colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = {
                            Text("Project name", color = AiTheme.colors.textSecondary)
                        },
                        placeholder = {
                            Text(if (sourceMode == ProjectSourceMode.GITHUB) "Uses repository name if empty" else "Shown in workspace")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = workspaceTextFieldColors(),
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description", color = AiTheme.colors.textSecondary) },
                        placeholder = { Text("Optional context for the agent") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = workspaceTextFieldColors(),
                    )
                }

                item {
                    var showAdvanced by remember { mutableStateOf(false) }
                    GlassCard(accentColor = AiTheme.colors.aiPrimary) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAdvanced = !showAdvanced },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Approval mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AiTheme.colors.textPrimary)
                                    Text(selectedTrustMode.displayName, style = MonoCaption, color = AiTheme.colors.textSecondary)
                                }
                                Icon(
                                    if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = AiTheme.colors.textSecondary,
                                )
                            }
                            if (showAdvanced) {
                                Spacer(Modifier.height(10.dp))
                                TrustMode.entries.forEach { mode ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (mode == selectedTrustMode) AiTheme.colors.aiPrimary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .clickable { selectedTrustMode = mode }
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = mode == selectedTrustMode,
                                            onClick = { selectedTrustMode = mode },
                                            colors = RadioButtonDefaults.colors(selectedColor = AiTheme.colors.aiPrimary),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(mode.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = AiTheme.colors.textPrimary)
                                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            when (sourceMode) {
                                ProjectSourceMode.DEVICE -> {
                                    val uri = workspaceUri ?: return@Button
                                    viewModel.createProjectFromWorkspace(name, uri, description, selectedTrustMode)
                                }
                                ProjectSourceMode.GITHUB -> {
                                    viewModel.createGitHubRemoteProject(
                                        repo = repoUrl,
                                        branch = branch,
                                        token = githubToken,
                                        name = name,
                                        description = description,
                                        trustMode = selectedTrustMode,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AiTheme.colors.aiPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                if (sourceMode == ProjectSourceMode.GITHUB) Icons.Filled.Cloud else Icons.Filled.Add,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (sourceMode == ProjectSourceMode.GITHUB) "Connect Repository" else "Create Workspace")
                        }
                    }
                }

                item {
                    if (createState is ProjectViewModel.CreateProjectState.Error) {
                        GlassCard(accentColor = AiTheme.colors.aiError) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = AiTheme.colors.aiError, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    (createState as ProjectViewModel.CreateProjectState.Error).message,
                                    color = AiTheme.colors.aiError,
                                    style = MaterialTheme.typography.bodySmall,
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
private fun SourceOptionCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.height(96.dp),
        accentColor = if (selected) AiTheme.colors.aiPrimary else Color.Transparent,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = if (selected) AiTheme.colors.aiPrimary else AiTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            }
            Text(
                subtitle,
                style = MonoCaption,
                color = AiTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun workspaceTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AiTheme.colors.aiPrimary,
    unfocusedBorderColor = AiTheme.colors.surfaceBorder,
    focusedContainerColor = AiTheme.colors.glassBase,
    unfocusedContainerColor = AiTheme.colors.glassBase,
    focusedTextColor = AiTheme.colors.textPrimary,
    unfocusedTextColor = AiTheme.colors.textPrimary,
    cursorColor = AiTheme.colors.aiPrimary
)

// Extension for TaskStatus color
@Composable
private fun TaskStatus.color(): Color = when (this) {
    TaskStatus.QUEUED -> AiTheme.colors.textSecondary
    TaskStatus.PLANNING -> AiTheme.colors.aiPrimary
    TaskStatus.READING_CONTEXT -> AiTheme.colors.aiPrimary
    TaskStatus.EDITING -> AiTheme.colors.aiSecondary
    TaskStatus.RUNNING_LOOP -> AiTheme.colors.aiPrimary
    TaskStatus.VERIFYING -> AiTheme.colors.aiPrimary
    TaskStatus.WAITING_APPROVAL -> AiTheme.colors.aiWarning
    TaskStatus.EXECUTING -> AiTheme.colors.aiSecondary
    TaskStatus.RETRYING -> AiTheme.colors.aiWarning
    TaskStatus.COMPLETED -> AiTheme.colors.aiSuccess
    TaskStatus.FAILED -> AiTheme.colors.aiError
    TaskStatus.ROLLED_BACK -> AiTheme.colors.aiWarning
    TaskStatus.PAUSED -> AiTheme.colors.textSecondary
}

private fun TaskStatus.isLive(): Boolean = this in setOf(
    TaskStatus.PLANNING,
    TaskStatus.READING_CONTEXT,
    TaskStatus.EDITING,
    TaskStatus.RUNNING_LOOP,
    TaskStatus.VERIFYING,
    TaskStatus.EXECUTING,
    TaskStatus.RETRYING,
)

private fun formatCompact(value: Int): String = when {
    value >= 1_000_000 -> "${"%.1f".format(value / 1_000_000.0)}M"
    value >= 1_000 -> "${"%.1f".format(value / 1_000.0)}K"
    else -> value.toString()
}
