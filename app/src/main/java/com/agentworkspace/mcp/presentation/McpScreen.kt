package com.agentworkspace.mcp.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.mcp.McpStatus
import com.agentworkspace.mcp.NeedMcp
import com.agentworkspace.shell.theme.AppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(
    onOpenDrawer: () -> Unit,
    viewModel: McpViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKeyInput.collectAsStateWithLifecycle()
    val styles by viewModel.styles.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.selectedStyle.collectAsStateWithLifecycle()
    val result by viewModel.generateResult.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = NeedMcp.UI_STUDIO_TITLE,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { McpStatusChip(status) }

            if (status is McpStatus.Connecting) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AppTheme.colors.accent,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Connecting to ${NeedMcp.DISPLAY_NAME}…", color = AppTheme.colors.textSecondary)
                        }
                    }
                }
            } else if (status !is McpStatus.Connected) {
                item {
                    ConnectCard(
                        apiKey = apiKey,
                        error = (status as? McpStatus.Error)?.message,
                        onApiKeyChange = viewModel::onApiKeyChange,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                    )
                }
            }

            if (status is McpStatus.Connected) {
                item { SectionLabel("1 · Pick a style") }
                item {
                    StyleStrip(
                        styles = styles,
                        selected = selectedStyle,
                        onSelect = viewModel::selectStyle,
                        onLoadMore = viewModel::loadStyles,
                    )
                }

                item { SectionLabel("2 · Generate") }
                item {
                    GenerateGrid(
                        selectedStyle = selectedStyle,
                        onGenerate = viewModel::generate,
                    )
                }

                item { SectionLabel("Output") }
                item { ResultPanel(result = result) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Powered by ${NeedMcp.DISPLAY_NAME} · also callable by the agent",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textDisabled,
                        )
                        Button(
                            onClick = viewModel::disconnect,
                            colors = ButtonDefaults.textButtonColors(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("Disconnect", color = AppTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun McpStatusChip(status: McpStatus) {
    val (label, dotColor) = when (status) {
        is McpStatus.Connected -> "Connected" to AppTheme.colors.accent
        is McpStatus.Connecting -> "Connecting…" to AppTheme.colors.textSecondary
        is McpStatus.Error -> "Error" to AppTheme.colors.error
        McpStatus.Disconnected -> "Not connected" to AppTheme.colors.textDisabled
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        if (status is McpStatus.Connected) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "· ${status.serverName}",
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.textDisabled,
            )
        }
    }
}

@Composable
private fun ConnectCard(
    apiKey: String,
    error: String?,
    onApiKeyChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = "Generate UI styles, components, layouts & wireframes from ${NeedMcp.DISPLAY_NAME}.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("NeedMCP API key (optional)") },
                placeholder = { Text("nmcp_…  ·  browse public styles anonymously") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = AppTheme.colors.textDisabled, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = mcpTextFieldColors(),
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = AppTheme.colors.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Connect", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Get a key at needmcp.com/api-keys. Stored encrypted on device.",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textDisabled,
            )
        }
    }
}

@Composable
private fun StyleStrip(
    styles: List<McpStyleItem>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onLoadMore: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StyleChip(label = "All", slug = null, selected = selected == null, onClick = onSelect)
                styles.forEach { style ->
                    StyleChip(label = style.name, slug = style.slug, selected = selected == style.slug, onClick = onSelect)
                }
            }
            if (styles.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No styles loaded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textDisabled,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onLoadMore, colors = ButtonDefaults.textButtonColors()) {
                    Text("Load styles", color = AppTheme.colors.accent)
                }
            }
        }
    }
}

@Composable
private fun StyleChip(
    label: String,
    slug: String?,
    selected: Boolean,
    onClick: (String?) -> Unit,
) {
    val bg = if (selected) AppTheme.colors.accent else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else AppTheme.colors.textSecondary
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AppTheme.colors.accent else AppTheme.colors.border),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable { onClick(slug) },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun GenerateGrid(
    selectedStyle: String?,
    onGenerate: (UiGenerateKind) -> Unit,
) {
    val kinds = UiGenerateKind.entries
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        kinds.chunked(2).forEach { rowKinds ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowKinds.forEach { kind ->
                    GenerateButton(
                        kind = kind,
                        enabled = !kind.needsStyle || selectedStyle != null,
                        onClick = { onGenerate(kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowKinds.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GenerateButton(
    kind: UiGenerateKind,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (enabled) AppTheme.colors.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = kind.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else AppTheme.colors.textDisabled,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = kind.hint,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) AppTheme.colors.textSecondary else AppTheme.colors.textDisabled,
            )
        }
    }
}

@Composable
private fun ResultPanel(result: McpGenerateResult?) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            when {
                result == null -> {
                    Text(
                        text = "Pick a style, then generate to see design output here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textDisabled,
                    )
                }
                result.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppTheme.colors.accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Generating ${result.kind.label.lowercase()}…", color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                result.error != null -> {
                    Text("Error", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.error)
                    Spacer(Modifier.height(4.dp))
                    Text(result.error, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.error)
                }
                result.text != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(result.kind.label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
                        val clipboard = LocalClipboardManager.current
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(result.text)) },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = AppTheme.colors.textDisabled, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = result.text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun mcpTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = AppTheme.colors.accent,
    unfocusedBorderColor = AppTheme.colors.border,
    cursorColor = AppTheme.colors.accent,
    focusedLabelColor = AppTheme.colors.textSecondary,
    unfocusedLabelColor = AppTheme.colors.textDisabled,
)
