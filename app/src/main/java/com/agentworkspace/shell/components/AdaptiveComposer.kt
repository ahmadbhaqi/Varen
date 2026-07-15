package com.agentworkspace.shell.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.agentworkspace.shell.presentation.HomeModelOption
import com.agentworkspace.shell.theme.AppTheme

enum class AdaptiveRunningAction {
    Stop,
    OpenTask,
}

@Composable
fun AdaptiveComposer(
    value: String,
    onValueChange: (String) -> Unit,
    isRunning: Boolean,
    modelLabel: String,
    modelOptions: List<HomeModelOption>,
    onModelSelected: (HomeModelOption) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onRunningAction: () -> Unit,
    modifier: Modifier = Modifier,
    hasProjectContext: Boolean = false,
    onToolMode: (() -> Unit)? = null,
    onConfigureModels: (() -> Unit)? = null,
    runningAction: AdaptiveRunningAction = AdaptiveRunningAction.Stop,
    placeholder: String = "Ask anything or describe a task",
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 160.dp)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = AppTheme.colors.textPrimary,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.textPrimary),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                color = AppTheme.colors.textDisabled,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AdaptiveIconButton(
                    icon = if (hasProjectContext) Icons.Filled.Folder else Icons.Filled.AttachFile,
                    contentDescription = if (hasProjectContext) "Open project context" else "Attach project",
                    onClick = onAttach,
                )

                Box {
                    Surface(
                        onClick = { modelMenuExpanded = true },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .widthIn(max = 124.dp)
                            .semantics { contentDescription = "Choose model" },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        contentColor = AppTheme.colors.textSecondary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = modelLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                        offset = DpOffset(0.dp, (-8).dp),
                    ) {
                        if (modelOptions.isEmpty()) {
                            Text(
                                text = "No models available",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = AppTheme.colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            modelOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = option.name,
                                                color = if (option.enabled) {
                                                    AppTheme.colors.textPrimary
                                                } else {
                                                    AppTheme.colors.textDisabled
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                            Text(
                                                text = option.detail,
                                                color = AppTheme.colors.textDisabled,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    },
                                    onClick = {
                                        modelMenuExpanded = false
                                        onModelSelected(option)
                                    },
                                    enabled = option.enabled,
                                    trailingIcon = if (option.selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        if (modelOptions.isEmpty() || modelOptions.any { !it.enabled }) {
                            onConfigureModels?.let { configure ->
                                DropdownMenuItem(
                                    text = { Text("Configure connections") },
                                    onClick = {
                                        modelMenuExpanded = false
                                        configure()
                                    },
                                )
                            }
                        }
                    }
                }

                onToolMode?.let { configureTools ->
                    AdaptiveIconButton(
                        icon = Icons.Filled.Tune,
                        contentDescription = "Configure tools",
                        onClick = configureTools,
                    )
                }

                Spacer(Modifier.weight(1f))

                Surface(
                    onClick = if (isRunning) onRunningAction else onSend,
                    enabled = isRunning || value.isNotBlank(),
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = if (isRunning || value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        AppTheme.colors.elevated
                    },
                    contentColor = if (isRunning || value.isNotBlank()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        AppTheme.colors.textDisabled
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                !isRunning -> Icons.AutoMirrored.Filled.Send
                                runningAction == AdaptiveRunningAction.OpenTask -> Icons.AutoMirrored.Filled.ArrowForward
                                else -> Icons.Filled.Stop
                            },
                            contentDescription = when {
                                !isRunning -> "Send prompt"
                                runningAction == AdaptiveRunningAction.OpenTask -> "Open running task"
                                else -> "Stop task"
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
