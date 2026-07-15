package com.agentworkspace.task

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.shell.components.AdaptiveIconButton
import com.agentworkspace.shell.components.AdaptiveScreen
import com.agentworkspace.shell.components.AdaptiveTopBar
import com.agentworkspace.shell.theme.AiTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    projectId: String,
    taskId: String,
    onDiffClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel(),
) {
    val checkpointsFlow = remember(taskId) { viewModel.getCheckpoints(taskId) }
    val task by viewModel.task.collectAsStateWithLifecycle()
    val checkpoints by checkpointsFlow.collectAsStateWithLifecycle(emptyList())
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val pending by viewModel.pendingApproval.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var showDetails by remember { mutableStateOf(false) }
    var composerPrefill by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectId, taskId) {
        viewModel.bindTask(projectId, taskId)
    }

    LaunchedEffect(events.size, isRunning) {
        val target = events.size + if (isRunning) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    AdaptiveScreen {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            ConversationTopBar(
                task = task,
                isRunning = isRunning,
                waitingApproval = pending != null,
                onBack = onBack,
                onDetails = { showDetails = true },
            )
            HorizontalDivider(color = AiTheme.colors.surfaceBorder)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (events.isEmpty() && !isRunning) {
                        item {
                            ChatEmptyState(
                                onPrompt = { composerPrefill = it },
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    } else {
                        items(events.size) { i -> ChatMessageItem(events[i]) }
                        if (isRunning) {
                            item { AgentThinkingRow() }
                        }
                    }
                }
            }

            pending?.let {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ApprovalCard(
                        approval = it,
                        onApprove = { viewModel.respondApproval(true) },
                        onDeny = { viewModel.respondApproval(false) },
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .align(Alignment.Center)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            HorizontalDivider(color = AiTheme.colors.surfaceBorder)
            Box(modifier = Modifier.fillMaxWidth()) {
                ChatComposer(
                    isRunning = isRunning,
                    prefill = composerPrefill,
                    onPrefillConsumed = { composerPrefill = "" },
                    onCancel = { viewModel.cancelCurrent() },
                    onSubmit = { goal -> viewModel.runTask(projectId, taskId, goal) },
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .align(Alignment.Center)
                        .navigationBarsPadding(),
                )
            }
        }
    }

    if (showDetails) {
        ModalBottomSheet(
            onDismissRequest = { showDetails = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            TaskDetailsSheet(
                task = task,
                checkpoints = checkpoints.size,
                onReviewDiff = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showDetails = false
                        onDiffClick(taskId)
                    }
                },
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
@Composable
private fun ConversationTopBar(
    task: Task?,
    isRunning: Boolean,
    waitingApproval: Boolean,
    onBack: () -> Unit,
    onDetails: () -> Unit,
) {
    AdaptiveTopBar(
        title = task?.title?.ifBlank { "Agent Task" } ?: "Agent Task",
        subtitle = taskStatusLabel(task = task, isRunning = isRunning, waitingApproval = waitingApproval),
        leading = {
            AdaptiveIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        },
        actions = {
            AdaptiveIconButton(Icons.Filled.Tune, "Task details", onDetails)
        },
    )
}

private fun taskStatusLabel(task: Task?, isRunning: Boolean, waitingApproval: Boolean): String {
    val status = task?.status
    return when {
        waitingApproval -> "Waiting for approval"
        isRunning -> status?.displayName ?: "Working"
        status == TaskStatus.COMPLETED -> "Completed"
        status == TaskStatus.FAILED -> "Needs attention"
        status == null -> "Loading"
        else -> "Ready"
    }
}
