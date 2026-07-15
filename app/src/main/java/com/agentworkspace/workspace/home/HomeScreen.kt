package com.agentworkspace.workspace.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.shell.components.AdaptiveComposer
import com.agentworkspace.shell.components.AdaptiveContextStrip
import com.agentworkspace.shell.components.AdaptiveIconButton
import com.agentworkspace.shell.components.AdaptiveAgentModule
import com.agentworkspace.shell.components.AdaptiveScreen
import com.agentworkspace.shell.components.AdaptiveTopBar
import com.agentworkspace.shell.components.AdaptiveRunningAction
import com.agentworkspace.shell.presentation.ContextualAgentCardSpec
import com.agentworkspace.shell.presentation.HomeConversationSendTarget
import com.agentworkspace.shell.presentation.HomePendingPromptAction
import com.agentworkspace.shell.presentation.WorkspaceConversationMode
import com.agentworkspace.shell.presentation.composerModelLabel
import com.agentworkspace.shell.presentation.contextualCardsForTask
import com.agentworkspace.shell.presentation.homeActionProjectId
import com.agentworkspace.shell.presentation.homeActiveProject
import com.agentworkspace.shell.presentation.homeConversationMode
import com.agentworkspace.shell.presentation.homeConversationSendTarget
import com.agentworkspace.shell.presentation.homePendingPromptAction
import com.agentworkspace.shell.presentation.homeWorkspaceContextSummary
import com.agentworkspace.shell.presentation.isLiveAgentStatus
import com.agentworkspace.shell.theme.AppTheme

@Composable
fun HomeScreen(
    onProjectClick: (String) -> Unit,
    onNewProject: () -> Unit,
    onTaskClick: (projectId: String, taskId: String) -> Unit,
    onConfigureModels: () -> Unit,
    onOpenDrawer: () -> Unit,
    onProjectMenuClick: (hasProject: Boolean) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTask = uiState.activeTasks.firstOrNull()
    val activeProject = homeActiveProject(activeTask, uiState.recentProjects, uiState.activeTaskProject)
    val actionProjectId = homeActionProjectId(activeTask, activeProject)
    val activeModelName = activeTask?.modelId
        ?.takeIf { it.isNotBlank() }
        ?: uiState.modelOptions.firstOrNull { it.selected }?.name
        ?: uiState.activeModelName
    val contextSummary = homeWorkspaceContextSummary(
        activeTask = activeTask,
        recentProjects = uiState.recentProjects,
        activeTaskProject = uiState.activeTaskProject,
        defaultModelName = activeModelName,
        defaultTrustMode = uiState.trustMode,
        connectionStatus = uiState.connectionStatus,
    )
    val contextualCards = contextualCardsForTask(activeTask)
    val agentRunning = activeTask?.status?.let(::isLiveAgentStatus) == true
    var composer by rememberSaveable { mutableStateOf("") }
    var unscopedPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    var hasSentFirstPrompt by rememberSaveable { mutableStateOf(false) }
    val pendingPromptAction = homePendingPromptAction(actionProjectId, unscopedPrompt)
    val conversationMode = homeConversationMode(
        savedPrompt = if (hasSentFirstPrompt) unscopedPrompt ?: "submitted" else null,
        activeTask = activeTask,
    )

    fun startProjectTask(projectId: String, prompt: String) {
        hasSentFirstPrompt = true
        composer = ""
        viewModel.createAndStartTask(projectId, prompt) { taskId ->
            unscopedPrompt = null
            onTaskClick(projectId, taskId)
        }
    }

    fun submitHomeMessage() {
        when (val target = homeConversationSendTarget(actionProjectId, composer)) {
            HomeConversationSendTarget.None -> return
            is HomeConversationSendTarget.ContinueWithoutProject -> {
                hasSentFirstPrompt = true
                unscopedPrompt = target.prompt
                composer = ""
            }
            is HomeConversationSendTarget.StartProjectTask -> {
                startProjectTask(target.projectId, target.prompt)
            }
        }
    }

    fun resumeUnscopedPrompt() {
        when (val action = pendingPromptAction) {
            HomePendingPromptAction.None -> return
            is HomePendingPromptAction.AttachProject -> onNewProject()
            is HomePendingPromptAction.RunInProject -> startProjectTask(action.projectId, action.prompt)
        }
    }

    AdaptiveScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AdaptiveTopBar(
                title = activeProject?.name ?: "Workspace",
                subtitle = when {
                    activeTask != null -> activeTask.status.displayName
                    actionProjectId != null -> "Ready to build"
                    else -> "AI workspace"
                },
                leading = {
                    AdaptiveIconButton(
                        icon = Icons.Filled.Menu,
                        contentDescription = "Open navigation",
                        onClick = onOpenDrawer,
                    )
                },
                actions = {
                    AdaptiveIconButton(
                        icon = if (actionProjectId == null) Icons.Filled.Add else Icons.Filled.MoreVert,
                        contentDescription = if (actionProjectId == null) "New project" else "Workspace actions",
                        onClick = {
                            if (actionProjectId == null) onNewProject() else onProjectMenuClick(true)
                        },
                    )
                },
            )

            AdaptiveContextStrip(
                projectTitle = contextSummary.projectTitle,
                meta = contextSummary.metaLine.replace(" - ", " · "),
                status = when {
                    agentRunning -> "Working"
                    uiState.connectionStatus == "Connected" -> "Ready"
                    else -> "Offline"
                },
                onClick = { actionProjectId?.let(onProjectClick) ?: onNewProject() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )

            ConversationTimeline(
                conversationMode = conversationMode,
                activeTask = activeTask,
                contextualCards = contextualCards,
                unscopedPrompt = unscopedPrompt,
                hasProject = actionProjectId != null,
                onPrompt = { composer = it },
                onTaskClick = { task -> onTaskClick(task.projectId, task.id) },
                onAttachProject = onNewProject,
                canResumePrompt = pendingPromptAction is HomePendingPromptAction.RunInProject,
                onResumePrompt = ::resumeUnscopedPrompt,
                modifier = Modifier.weight(1f),
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                AdaptiveComposer(
                    value = composer,
                    onValueChange = { composer = it },
                    isRunning = agentRunning,
                    modelLabel = composerModelLabel(activeModelName),
                    modelOptions = uiState.modelOptions,
                    onModelSelected = { option ->
                        actionProjectId?.let { projectId ->
                            viewModel.useModelForProject(projectId, option)
                        }
                    },
                    onAttach = { actionProjectId?.let(onProjectClick) ?: onNewProject() },
                    hasProjectContext = actionProjectId != null,
                    onConfigureModels = onConfigureModels,
                    onSend = ::submitHomeMessage,
                    runningAction = AdaptiveRunningAction.OpenTask,
                    onRunningAction = { activeTask?.let { onTaskClick(it.projectId, it.id) } },
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .align(Alignment.Center)
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                        .navigationBarsPadding(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConversationTimeline(
    conversationMode: WorkspaceConversationMode,
    activeTask: Task?,
    contextualCards: List<ContextualAgentCardSpec>,
    unscopedPrompt: String?,
    hasProject: Boolean,
    onPrompt: (String) -> Unit,
    onTaskClick: (Task) -> Unit,
    onAttachProject: () -> Unit,
    canResumePrompt: Boolean,
    onResumePrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "empty-hero") {
                AdaptiveEmptyHero(
                    visible = conversationMode == WorkspaceConversationMode.Empty,
                    hasProject = hasProject,
                    onPrompt = onPrompt,
                    onAttachProject = onAttachProject,
                )
            }

            unscopedPrompt?.let { prompt ->
                item(key = "unscoped-prompt") { UserMessage(text = prompt) }
                item(key = "unscoped-notice") {
                    ProjectContextNotice(
                        canResumePrompt = canResumePrompt,
                        onAttachProject = onAttachProject,
                        onResumePrompt = onResumePrompt,
                    )
                }
            }

            activeTask?.let { task ->
                item(key = "task-prompt-${task.id}") {
                    UserMessage(text = task.goal.ifBlank { task.title })
                }
                item(key = "task-reply-${task.id}") {
                    AgentMessage(task = task, onClick = { onTaskClick(task) })
                }
                contextualCards.forEach { card ->
                    item(key = "${task.id}-${card.kind}") {
                        AdaptiveAgentModule(card = card, onOpen = { onTaskClick(task) })
                    }
                }
            }
        }
    }
}

@Composable
fun AdaptiveEmptyHero(
    visible: Boolean,
    hasProject: Boolean,
    onPrompt: (String) -> Unit,
    onAttachProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slideDistance = with(LocalDensity.current) { 12.dp.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(280)) + expandVertically(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(tween(180)) +
            slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { -slideDistance } +
            shrinkVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = AppTheme.colors.textPrimary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "What do you want to build?",
                style = MaterialTheme.typography.headlineLarge,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasProject) {
                    "Describe the outcome. Your workspace context is ready."
                } else {
                    "Start with an idea, or attach a project for workspace-aware changes."
                },
                modifier = Modifier.widthIn(max = 460.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(28.dp))
            val suggestions = listOf(
                "Explain this project" to "Explain this project's architecture and the best next improvement.",
                "Fix the current build" to "Find the current build failure, fix it, and verify the result.",
            )
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { (title, prompt) ->
                    SuggestionRow(title = title, onClick = { onPrompt(prompt) })
                }
                if (!hasProject) {
                    Surface(
                        onClick = onAttachProject,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Attach a project", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = AppTheme.colors.textDisabled,
            )
        }
    }
}

@Composable
private fun UserMessage(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun AgentMessage(task: Task, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = AppTheme.colors.elevated,
            border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = AppTheme.colors.textPrimary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Agent",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(task.status.displayName)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = task.outputSummary.ifBlank { statusActivityLabel(task.status) },
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppTheme.colors.elevated,
        border = BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textDisabled,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProjectContextNotice(
    canResumePrompt: Boolean,
    onAttachProject: () -> Unit,
    onResumePrompt: () -> Unit,
) {
    Surface(
        onClick = if (canResumePrompt) onResumePrompt else onAttachProject,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (canResumePrompt) "Run in project" else "Attach project context",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (canResumePrompt) {
                        "The saved message is ready for this workspace."
                    } else {
                        "Attach a project before the agent edits files or runs tools."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDisabled,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = AppTheme.colors.textDisabled,
            )
        }
    }
}

private fun statusActivityLabel(status: TaskStatus): String = when (status) {
    TaskStatus.PLANNING -> "Planning the next steps."
    TaskStatus.READING_CONTEXT -> "Reading project context."
    TaskStatus.EDITING, TaskStatus.RUNNING_LOOP -> "Editing files."
    TaskStatus.VERIFYING -> "Verifying the result."
    TaskStatus.WAITING_APPROVAL -> "Waiting for your review."
    TaskStatus.EXECUTING -> "Running a command."
    TaskStatus.RETRYING -> "Retrying the current step."
    TaskStatus.COMPLETED -> "The result is ready for review."
    TaskStatus.FAILED -> "This run needs attention."
    TaskStatus.ROLLED_BACK -> "Changes were rolled back."
    TaskStatus.PAUSED -> "The task is paused."
    TaskStatus.QUEUED -> "The task is queued."
}
