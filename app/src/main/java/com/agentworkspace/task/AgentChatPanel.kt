package com.agentworkspace.task

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import com.agentworkspace.shell.components.modern.StatusBadge
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption

// ---------------------------------------------------------------------------
// Conversation rendering
//
// A calm, professional chat surface: right-aligned accent bubbles for the
// operator, left-aligned assistant turns with a compact avatar, and quiet
// monospace rows for tool activity so the transcript reads like a real agent
// session rather than a log dump.
// ---------------------------------------------------------------------------

import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun ChatMessageItem(line: ChatLine) {
    when (line.role) {
        ChatLine.Role.USER -> UserTurn(line.text)
        ChatLine.Role.ASSISTANT -> AssistantTurn(line.text)
        ChatLine.Role.TOOL -> ToolActivityRow(line.text, AiTheme.colors.textSecondary, Icons.Filled.Terminal)
        ChatLine.Role.TOOL_RESULT -> ToolActivityRow(line.text, AiTheme.colors.aiSuccess, Icons.Filled.Check)
        ChatLine.Role.SYSTEM -> SystemNote(line.text)
        ChatLine.Role.ERROR -> ErrorTurn(line.text)
    }
}

@Composable
private fun UserTurn(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .background(AppTheme.colors.elevated)
                .border(
                    BorderStroke(1.dp, AppTheme.colors.border),
                    RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = AiTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun AssistantTurn(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        AgentAvatar()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Antigravity Agent",
                    style = MaterialTheme.typography.titleSmall,
                    color = AiTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.textPrimary)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = AiTheme.colors.textPrimary,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun AgentAvatar() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.elevated)
            .border(
                BorderStroke(1.dp, AppTheme.colors.borderStrong),
                CircleShape
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = AppTheme.colors.textPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ToolActivityRow(text: String, tint: Color, icon: ImageVector) {
    var expanded by remember { mutableStateOf(false) }
    val isSuccess = tint == AiTheme.colors.aiSuccess
    val isError = tint == AiTheme.colors.aiError
    val statusText = when {
        isSuccess -> "COMPLETED"
        isError -> "FAILED"
        else -> "EXECUTING"
    }
    val badgeColor = when {
        isSuccess -> AiTheme.colors.aiSuccess
        isError -> AiTheme.colors.aiError
        else -> AiTheme.colors.aiPrimary
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Spacer(Modifier.width(46.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(AiTheme.colors.glassBase)
                .border(
                    BorderStroke(1.dp, AiTheme.colors.surfaceBorder.copy(alpha = 0.5f)),
                    RoundedCornerShape(12.dp)
                )
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (text.startsWith("Tool execution: ")) text.removePrefix("Tool execution: ").substringBefore("\n") else "Running command",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AiTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(
                        text = statusText,
                        color = badgeColor,
                        pulse = !isSuccess && !isError,
                        modifier = Modifier.scale(0.8f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = AiTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(BorderStroke(1.dp, AiTheme.colors.surfaceBorder.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = AiTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemNote(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text = text,
            style = MonoCaption,
            color = AiTheme.colors.textTertiary,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun ErrorTurn(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Spacer(Modifier.width(44.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                .border(BorderStroke(1.dp, AiTheme.colors.aiError.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
internal fun AgentThinkingRow() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AgentAvatar()
        Spacer(Modifier.width(12.dp))
        val transition = rememberInfiniteTransition(label = "thinking")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(600, delayMillis = index * 160, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                    label = "dot",
                )
                Box(
                    Modifier
                        .size(6.dp)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(AiTheme.colors.textSecondary),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Composer
// ---------------------------------------------------------------------------

@Composable
internal fun ChatComposer(
    isRunning: Boolean,
    prefill: String,
    onPrefillConsumed: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(prefill) {
        if (prefill.isNotEmpty()) {
            input = prefill
            onPrefillConsumed()
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                enabled = !isRunning,
                maxLines = 4,
                placeholder = { Text("Message agent...", color = AiTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium) },
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AiTheme.colors.aiPrimary.copy(alpha = 0.6f),
                    unfocusedBorderColor = AiTheme.colors.surfaceBorder.copy(alpha = 0.6f),
                    disabledBorderColor = AiTheme.colors.surfaceBorder.copy(alpha = 0.2f),
                    focusedContainerColor = AiTheme.colors.glassBase,
                    unfocusedContainerColor = AiTheme.colors.glassBase,
                    disabledContainerColor = AiTheme.colors.glassBase.copy(alpha = 0.3f),
                    cursorColor = AiTheme.colors.aiPrimary,
                    focusedTextColor = AiTheme.colors.textPrimary,
                    unfocusedTextColor = AiTheme.colors.textPrimary,
                ),
            )
            Spacer(Modifier.width(10.dp))
            val canSend = input.isNotBlank()
            FilledIconButton(
                enabled = isRunning || canSend,
                onClick = {
                    if (isRunning) {
                        onCancel()
                    } else {
                        val goal = input.trim()
                        if (goal.isNotEmpty()) {
                            keyboard?.hide()
                            onSubmit(goal)
                            input = ""
                        }
                    }
                },
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRunning) AiTheme.colors.aiError else AiTheme.colors.aiPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = AiTheme.colors.glassBase.copy(alpha = 0.4f),
                    disabledContentColor = AiTheme.colors.textTertiary,
                ),
            ) {
                Icon(
                    if (isRunning) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isRunning) "Stop" else "Send",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty conversation state with quick-start prompts
// ---------------------------------------------------------------------------

internal data class QuickPrompt(val label: String, val icon: ImageVector, val prompt: String)

internal val quickPrompts = listOf(
    QuickPrompt("Explain this codebase", Icons.Filled.Code, "Give me a concise, high-level overview of this codebase: structure, main modules, and how they fit together."),
    QuickPrompt("Fix the build", Icons.Filled.Warning, "Find why the build is failing, explain the root cause, fix it, and verify the app compiles."),
    QuickPrompt("Build debug APK", Icons.Filled.Build, "Build the debug APK and give me the generated APK path."),
)

@Composable
internal fun ChatEmptyState(onPrompt: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppTheme.colors.elevated)
                .border(
                    BorderStroke(1.dp, AppTheme.colors.borderStrong),
                    RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AiTheme.colors.aiPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "How can I help?",
            style = MaterialTheme.typography.titleLarge,
            color = AiTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask for edits, reviews, builds, or an explanation of the project.",
            style = MaterialTheme.typography.bodyMedium,
            color = AiTheme.colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            quickPrompts.forEach { qp ->
                QuickPromptRow(qp, onClick = { onPrompt(qp.prompt) })
            }
        }
    }
}

@Composable
private fun QuickPromptRow(prompt: QuickPrompt, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AiTheme.colors.glassBase,
        border = BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AiTheme.colors.aiPrimary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(prompt.icon, contentDescription = null, tint = AiTheme.colors.aiPrimary, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(prompt.label, style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = AiTheme.colors.textTertiary, modifier = Modifier.size(14.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Approval card
// ---------------------------------------------------------------------------

@Composable
internal fun ApprovalCard(
    approval: PendingApproval,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AiTheme.colors.glassBase,
        border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AiTheme.colors.aiWarning.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = AiTheme.colors.aiWarning, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Approval Required", style = MaterialTheme.typography.titleSmall, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Agent wants to ${approval.label}", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            if (approval.reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(approval.reason, style = MonoCaption, color = AiTheme.colors.textSecondary)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AiTheme.colors.aiError.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AiTheme.colors.aiError),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Deny", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AiTheme.colors.aiPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Allow", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
