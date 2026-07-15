package com.agentworkspace.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.agentworkspace.shell.components.modern.StatusBadge
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.MonoCaption

@Composable
fun EditorScreen(
    filePath: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(filePath) { viewModel.load(filePath) }

    val lineCount = remember(state.content) { if (state.content.isEmpty()) 0 else state.content.split("\n").size }
    val status = when {
        state.saved -> "saved"
        state.dirty -> "unsaved"
        else -> "$lineCount lines"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        EditorHeader(
            filePath = filePath,
            status = status,
            dirty = state.dirty,
            onBack = onBack,
            onSave = { viewModel.save(filePath) },
        )
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AiTheme.colors.aiPrimary)
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Error", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.aiError)
                }
            }
            else -> {
                CodeEditor(content = state.content, lineCount = lineCount, onChange = { viewModel.onContentChange(it) }, modifier = Modifier.weight(1f))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(AiTheme.colors.surfaceBorder))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AiTheme.colors.glassBase)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(filePath, style = MonoCaption, color = AiTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(status.uppercase(), style = MonoCaption, color = if (state.dirty) AiTheme.colors.aiWarning else AiTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorHeader(
    filePath: String,
    status: String,
    dirty: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        Surface(
            color = AiTheme.colors.glassBase,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AiTheme.colors.textPrimary)
                }
                Icon(Icons.Filled.Code, contentDescription = null, tint = AiTheme.colors.aiPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        filePath.substringAfterLast("/").ifEmpty { "Editor" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AiTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(filePath, style = MonoCaption, color = AiTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusBadge(
                    text = status.uppercase(),
                    color = if (dirty) AiTheme.colors.aiWarning else AiTheme.colors.aiSuccess,
                    modifier = Modifier.padding(end = 6.dp)
                )
                IconButton(
                    onClick = onSave,
                    enabled = dirty,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = "Save",
                        tint = if (dirty) AiTheme.colors.aiPrimary else AiTheme.colors.textSecondary.copy(alpha = 0.4f)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AiTheme.colors.surfaceBorder))
    }
}

@Composable
private fun CodeEditor(
    content: String,
    lineCount: Int,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .width(46.dp)
                .fillMaxHeight()
                .background(AiTheme.colors.glassBase)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            repeat(lineCount.coerceAtLeast(1)) { i ->
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = AiTheme.colors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 10.dp, top = 1.dp, bottom = 1.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(AiTheme.colors.surfaceBorder)
        )
        OutlinedTextField(
            value = content,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = AiTheme.colors.textPrimary,
            ),
            shape = RoundedCornerShape(0.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                cursorColor = AiTheme.colors.aiPrimary,
                focusedTextColor = AiTheme.colors.textPrimary,
                unfocusedTextColor = AiTheme.colors.textPrimary,
            ),
        )
    }
}
