package com.agentworkspace.diff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.DiffStatus
import com.agentworkspace.diff.engine.DiffLineType
import androidx.compose.runtime.remember
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.modern.StatusBadge
import com.agentworkspace.shell.components.modern.EmptyStateModern
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption


@Composable
fun DiffViewerScreen(
    projectId: String,
    taskId: String,
    onBack: () -> Unit,
    viewModel: DiffViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(projectId, taskId) { viewModel.load(projectId, taskId) }

    val diff = state.diff
    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DiffHeader(
                title = diff?.filePath?.substringAfterLast("/")?.ifEmpty { "Diff" } ?: "Diff review",
                filePath = diff?.filePath,
                status = diff?.status,
                onBack = onBack,
            )
            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AiTheme.colors.aiPrimary)
                        }
                    }
                    diff == null -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            EmptyStateModern(
                                icon = Icons.Filled.Difference,
                                title = "No changes to review",
                                subtitle = "Diffs from this task will appear here.",
                                accent = AiTheme.colors.aiPrimary,
                            )
                        }
                    }
                    else -> {
                        val horizontalScrollState = rememberScrollState()
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(
                                items = state.lines,
                                key = { index, _ -> index },
                            ) { _, line ->
                                DiffLineRow(line.content, line.type, horizontalScrollState)
                            }
                        }
                    }
                }
            }
            if (diff != null && diff.status == DiffStatus.PENDING) {
                DiffActionBar(
                    added = state.added,
                    removed = state.removed,
                    onAccept = { viewModel.accept() },
                    onReject = { viewModel.reject() },
                )
            }
        }
    }
}

@Composable
private fun DiffHeader(
    title: String,
    filePath: String?,
    status: DiffStatus?,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            accent = AiTheme.colors.textPrimary
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AiTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(filePath ?: "Waiting for patch", style = MonoCaption, color = AiTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        status?.let {
            StatusBadge(text = it.displayName, color = diffStatusColor(it))
        }
    }
}

@Composable
private fun DiffLineRow(content: String, type: DiffLineType, horizontalScrollState: ScrollState) {
    val (bg, fg, prefix) = when (type) {
        DiffLineType.ADDED -> Triple(AppTheme.colors.diffAddBg, AppTheme.colors.diffAddText, "+")
        DiffLineType.REMOVED -> Triple(AppTheme.colors.diffRemoveBg, AppTheme.colors.diffRemoveText, "-")
        DiffLineType.UNCHANGED -> Triple(Color.Transparent, AiTheme.colors.textPrimary, " ")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .horizontalScroll(horizontalScrollState)
            .padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Text(
            prefix,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.width(18.dp),
        )
        Text(
            content,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            color = fg,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DiffActionBar(
    added: Int,
    removed: Int,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = AiTheme.colors.glassBase,
        border = BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "+$added",
                    style = MonoCaption,
                    color = AiTheme.colors.aiSuccess,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "-$removed",
                    style = MonoCaption,
                    color = AiTheme.colors.aiError,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Review Changes",
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AiTheme.colors.aiError.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AiTheme.colors.aiError),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = AiTheme.colors.aiError)
                    Spacer(Modifier.width(6.dp))
                    Text("Reject Changes", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AiTheme.colors.aiPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Accept Patch", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun diffStatusColor(status: DiffStatus) = when (status) {
    DiffStatus.PENDING -> AiTheme.colors.aiWarning
    DiffStatus.EDITED -> AiTheme.colors.aiPrimary
    DiffStatus.APPLIED -> AiTheme.colors.aiSuccess
    DiffStatus.ACCEPTED -> AiTheme.colors.aiSuccess
    DiffStatus.REJECTED -> AiTheme.colors.aiError
}
