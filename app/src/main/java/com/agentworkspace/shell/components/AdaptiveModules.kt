package com.agentworkspace.shell.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentworkspace.shell.presentation.ContextualAgentCardSpec
import com.agentworkspace.shell.presentation.ContextualCardKind
import com.agentworkspace.shell.theme.AppTheme

@Composable
fun AdaptiveAgentModule(
    card: ContextualAgentCardSpec,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = card.kind.moduleIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = AppTheme.colors.textSecondary,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = card.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.kind.moduleLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textDisabled,
                )
            }
            Text(
                text = card.body,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                lineHeight = 19.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Open task",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AppTheme.colors.textPrimary,
                )
            }
        }
    }
}

private fun ContextualCardKind.moduleLabel(): String = when (this) {
    ContextualCardKind.Planning -> "PLAN"
    ContextualCardKind.Execution -> "RUN"
    ContextualCardKind.Approval -> "REVIEW"
    ContextualCardKind.Diff -> "DIFF"
    ContextualCardKind.Checkpoint -> "SAVE"
    ContextualCardKind.Failure -> "ISSUE"
    ContextualCardKind.Result -> "RESULT"
    ContextualCardKind.Usage -> "USAGE"
}

private fun ContextualCardKind.moduleIcon(): ImageVector = when (this) {
    ContextualCardKind.Planning -> Icons.Filled.Description
    ContextualCardKind.Execution -> Icons.Filled.Terminal
    ContextualCardKind.Approval -> Icons.Filled.Check
    ContextualCardKind.Diff -> Icons.Filled.Description
    ContextualCardKind.Checkpoint -> Icons.Filled.Restore
    ContextualCardKind.Failure -> Icons.Filled.Warning
    ContextualCardKind.Result -> Icons.Filled.Check
    ContextualCardKind.Usage -> Icons.Filled.Analytics
}
