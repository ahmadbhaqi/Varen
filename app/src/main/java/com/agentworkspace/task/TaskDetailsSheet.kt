package com.agentworkspace.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.agentworkspace.data.model.AgentStep
import com.agentworkspace.data.model.StepStatus
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import androidx.compose.foundation.BorderStroke
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.MonoCaption
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.PlayArrow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Task details sheet
//
// The at-a-glance dashboard now lives behind a "Details" action instead of
// dominating the screen. Operators stay in the conversation and pull the
// metrics, plan, and changes only when they want them.
// ---------------------------------------------------------------------------

internal enum class DetailsSection(val label: String) {
    Overview("Overview"),
    Steps("Steps"),
    Changes("Changes"),
}

@Composable
internal fun TaskDetailsSheet(
    task: Task?,
    checkpoints: Int,
    onReviewDiff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableStateOf(DetailsSection.Overview) }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(AiTheme.colors.surfaceBorder),
        )
        Text("Task Details", style = MaterialTheme.typography.titleMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        SegmentedTabs(
            sections = DetailsSection.values().toList(),
            selected = section,
            onSelect = { section = it },
        )
        Spacer(Modifier.height(16.dp))
        when (section) {
            DetailsSection.Overview -> OverviewSection(task = task, checkpoints = checkpoints)
            DetailsSection.Steps -> StepsSection(task = task)
            DetailsSection.Changes -> ChangesSection(task = task, onReviewDiff = onReviewDiff)
        }
    }
}

@Composable
private fun SegmentedTabs(
    sections: List<DetailsSection>,
    selected: DetailsSection,
    onSelect: (DetailsSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AiTheme.colors.glassBase)
            .border(1.dp, AiTheme.colors.surfaceBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sections.forEach { item ->
            val active = item == selected
            androidx.compose.material3.Surface(
                onClick = { onSelect(item) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (active) AiTheme.colors.aiPrimary else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else AiTheme.colors.textSecondary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(task: Task?, checkpoints: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProgressBar(task)
        val formatCompactCount = { count: Long ->
            if (count >= 1000000) String.format(Locale.US, "%.1fM", count / 1000000.0)
            else if (count >= 1000) String.format(Locale.US, "%.1fK", count / 1000.0)
            else count.toString()
        }
        val usageValue = ((task?.usage?.inputTokens ?: 0) + (task?.usage?.outputTokens ?: 0)).toLong()
        val facts = listOf(
            Triple(Icons.Filled.Speed, "Model", task?.modelId?.substringAfterLast(".")?.ifEmpty { "Default" } ?: "Default"),
            Triple(Icons.Filled.PlayArrow, "Started", formatTaskTime(task?.createdAt)),
            Triple(Icons.Filled.FolderOpen, "Files read", (task?.filesRead?.size ?: 0).toString()),
            Triple(Icons.Filled.Description, "Files changed", (task?.filesChanged?.size ?: 0).toString()),
            Triple(Icons.Filled.Speed, "Usage", formatCompactCount(usageValue)),
            Triple(Icons.Filled.Check, "Checkpoints", checkpoints.toString()),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = AiTheme.colors.glassBase,
            border = BorderStroke(1.dp, AiTheme.colors.surfaceBorder),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                facts.forEachIndexed { index, (icon, label, value) ->
                    TaskFactRow(icon = icon, label = label, value = value)
                    if (index < facts.lastIndex) {
                        HorizontalDivider(color = AiTheme.colors.surfaceBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskFactRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AiTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = AiTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressBar(task: Task?) {
    val current = task?.currentStepIndex?.coerceAtLeast(0) ?: 0
    val total = task?.agentPlan?.size?.takeIf { it > 0 } ?: 0
    val percent = if (total > 0) (current * 100) / total else 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AiTheme.colors.glassBase)
            .border(1.dp, AiTheme.colors.surfaceBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("Step ${current.coerceAtMost(total)} of $total", style = MonoCaption, color = AiTheme.colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            task?.agentPlan?.getOrNull(current)?.description ?: task?.goal ?: "Waiting for task plan",
            style = MaterialTheme.typography.bodyMedium,
            color = AiTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape).background(AiTheme.colors.surfaceBorder),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(percent / 100f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(AiTheme.colors.aiPrimary),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("$percent%", style = MonoCaption, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepsSection(task: Task?) {
    val steps = task?.agentPlan.orEmpty()
    if (steps.isEmpty()) {
        DetailEmpty("No execution plan yet", "The agent will create steps after you send a goal.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(steps) { step -> StepRowItem(step) }
        }
    }
}

@Composable
private fun StepRowItem(step: AgentStep) {
    val color = stepColor(step.status)
    val borderColor = if (step.status == StepStatus.IN_PROGRESS) {
        AiTheme.colors.surfaceBorder
    } else {
        AiTheme.colors.surfaceBorder.copy(alpha = 0.55f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (step.status == StepStatus.IN_PROGRESS) AiTheme.colors.glassBase else Color.Transparent)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.dp, color.copy(alpha = 0.6f), CircleShape)
                .background(if (step.status == StepStatus.COMPLETED) color.copy(alpha = 0.08f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (step.status == StepStatus.COMPLETED) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            } else if (step.status == StepStatus.IN_PROGRESS) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(step.description, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ChangesSection(task: Task?, onReviewDiff: () -> Unit) {
    val changed = task?.filesChanged.orEmpty()
    val read = task?.filesRead.orEmpty()
    if (changed.isEmpty() && read.isEmpty()) {
        DetailEmpty("No file activity yet", "Files the agent reads or changes appear here.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        if (changed.isNotEmpty()) {
            item {
                androidx.compose.material3.Surface(
                    onClick = onReviewDiff,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AiTheme.colors.aiPrimary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, AiTheme.colors.aiPrimary.copy(alpha = 0.35f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Difference, contentDescription = null, tint = AiTheme.colors.aiPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Review proposed patch", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                            Text("${changed.size} changed files", style = MonoCaption, color = AiTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
        items(changed) { file -> FileRowItem(file, Icons.Filled.Description, AiTheme.colors.aiSuccess) }
        items(read) { file -> FileRowItem(file, Icons.Filled.FolderOpen, AiTheme.colors.textSecondary) }
    }
}

@Composable
private fun FileRowItem(file: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AiTheme.colors.glassBase)
            .border(1.dp, AiTheme.colors.surfaceBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            file,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = AiTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailEmpty(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun stepColor(status: StepStatus): Color = when (status) {
    StepStatus.COMPLETED -> AiTheme.colors.aiSuccess
    StepStatus.FAILED -> AiTheme.colors.aiError
    StepStatus.IN_PROGRESS -> AiTheme.colors.aiPrimary
    StepStatus.SKIPPED, StepStatus.PENDING -> AiTheme.colors.textSecondary
}

internal fun formatTaskTime(timestamp: Long?): String =
    timestamp?.let { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(it)) } ?: "Not started"
