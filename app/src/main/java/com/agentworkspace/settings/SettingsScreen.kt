package com.agentworkspace.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agentworkspace.shell.components.WorkspaceBrandMark
import com.agentworkspace.shell.components.WorkspaceDivider
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.components.WorkspaceSectionTitle
import com.agentworkspace.shell.components.modern.GlassCard
import com.agentworkspace.shell.components.bounceClick
import com.agentworkspace.shell.presentation.settingsSectionTitles
import com.agentworkspace.shell.presentation.settingsSystemActionTitles
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.MonoCaption

@Composable
fun SettingsScreen(
    onConnectionsClick: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val sections = settingsSectionTitles()
    val systemTitles = settingsSystemActionTitles()

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WorkspaceIconButton(
                        icon = Icons.Filled.Menu,
                        contentDescription = "Open navigation",
                        onClick = onOpenDrawer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = AiTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Workspace configuration and preferences",
                            style = MaterialTheme.typography.bodySmall,
                            color = AiTheme.colors.textSecondary,
                        )
                    }
                }
            }
            item {
                GlassCard(accentColor = AiTheme.colors.aiPrimary) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WorkspaceBrandMark(size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "Workspace IDE",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AiTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Distribution", style = MonoCaption, color = AiTheme.colors.textSecondary)
                                Spacer(Modifier.width(8.dp))
                                SettingsBadge("OSS")
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroup(title = sections[1]) {
                    GlassCard(
                        accentColor = AiTheme.colors.aiSecondary,
                        onClick = onConnectionsClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AiTheme.colors.aiSecondary.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Hub, contentDescription = null, tint = AiTheme.colors.aiSecondary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(systemTitles[0], style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("AI service providers and API keys", style = MonoCaption, color = AiTheme.colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    var selectedTheme by remember { mutableStateOf("Dark") }
    var selectedAccent by remember { mutableIntStateOf(0) }
    var fontScale by remember { mutableFloatStateOf(0.56f) }
    var reduceMotion by remember { mutableStateOf(false) }
    var compactMode by remember { mutableStateOf(false) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var useMonospace by remember { mutableStateOf(true) }

    val accentOptions = listOf(
        Color(0xFFFFFFFF) to "Pure White",
        Color(0xFFE5E5E5) to "Alabaster",
        Color(0xFFCCCCCC) to "Platinum",
        Color(0xFF8A8A8A) to "Slate Gray",
        Color(0xFF484848) to "Charcoal",
        Color(0xFF1E1E1E) to "Deep Obsidian",
    )

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SettingsSubHeader(title = "Appearance", onBack = onBack) }
            item {
                SettingsLabel("Theme Mode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ThemeChoiceCard(
                        title = "System",
                        subtitle = "Match device",
                        icon = Icons.Filled.Settings,
                        selected = selectedTheme == "System",
                        onClick = { selectedTheme = "System" },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeChoiceCard(
                        title = "Light",
                        subtitle = "Stark Light",
                        icon = Icons.Filled.LightMode,
                        selected = selectedTheme == "Light",
                        onClick = { selectedTheme = "Light" },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeChoiceCard(
                        title = "Dark",
                        subtitle = "OLED Dark",
                        icon = Icons.Filled.DarkMode,
                        selected = selectedTheme == "Dark",
                        onClick = { selectedTheme = "Dark" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                SettingsLabel("Monochrome Accent Swatch")
                GlassCard(accentColor = AiTheme.colors.aiPrimary) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        accentOptions.forEachIndexed { index, option ->
                            ColorSwatch(
                                color = option.first,
                                selected = selectedAccent == index,
                                onClick = { selectedAccent = index },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(accentOptions[selectedAccent].second.uppercase(), style = MonoCaption, color = AiTheme.colors.textSecondary)
                }
            }
            item {
                GlassCard(accentColor = AiTheme.colors.aiSecondary) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Font Scale", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Normal", style = MonoCaption, color = AiTheme.colors.textSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("A", style = MonoCaption, color = AiTheme.colors.textSecondary)
                        Slider(value = fontScale, onValueChange = { fontScale = it }, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        Text("A", style = MaterialTheme.typography.titleMedium, color = AiTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                SettingsToggleGroup {
                    SettingsToggleRow("Reduce Motion", "Minimize workspace animations", reduceMotion) { reduceMotion = it }
                    WorkspaceDivider()
                    SettingsToggleRow("Compact Workspace", "Denser UI padding constraints", compactMode) { compactMode = it }
                    WorkspaceDivider()
                    SettingsToggleRow("Show Line Numbers", "Display margins in code editor", showLineNumbers) { showLineNumbers = it }
                    WorkspaceDivider()
                    SettingsToggleRow("Use Monospace Fonts", "Apply monospace formatting in prompts", useMonospace) { useMonospace = it }
                }
            }
            item {
                SettingsLabel("Preview")
                CodePreviewPanel()
            }
        }
    }
}

@Composable
fun BehaviorSettingsScreen(onBack: () -> Unit) {
    var autoRun by remember { mutableStateOf(true) }
    var confirmBeforeRun by remember { mutableStateOf(true) }
    var streamResponses by remember { mutableStateOf(true) }
    var collapseOldMessages by remember { mutableStateOf(false) }
    var enableSuggestions by remember { mutableStateOf(true) }
    var autoImport by remember { mutableStateOf(true) }
    var inlineEdits by remember { mutableStateOf(true) }
    var showExplanation by remember { mutableStateOf(false) }
    var autoDetectContext by remember { mutableStateOf(true) }

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SettingsSubHeader(title = "Behavior", onBack = onBack) }
            item {
                SettingsLabel("Agent Loop Controls")
                SettingsToggleGroup {
                    SettingsToggleRow("Auto-Execute Actions", "Automatically run verified commands", autoRun) { autoRun = it }
                    WorkspaceDivider()
                    SettingsToggleRow("Confirm Before Execute", "Ask permission for critical terminal steps", confirmBeforeRun) { confirmBeforeRun = it }
                    WorkspaceDivider()
                    SettingsToggleRow("Stream Responses", "Show real-time reasoning and steps", streamResponses) { streamResponses = it }
                    WorkspaceDivider()
                    SettingsToggleRow("Collapse Old Steps", "Automatically fold previous agent tasks", collapseOldMessages) { collapseOldMessages = it }
                }
            }
            item {
                SettingsLabel("Assistance & Suggestions")
                SettingsToggleGroup {
                    SettingsToggleRow("Enable Recommendations", "Show helper code prompts", enableSuggestions) { enableSuggestions = it }
                    WorkspaceDivider()
                    SettingsToggleRow("Auto Import", "Automatically resolve file links", autoImport) { autoImport = it }
                    WorkspaceDivider()
                    SettingsToggleRow("Suggest Inline Diffs", "Request code diff review overlays", inlineEdits) { inlineEdits = it }
                    WorkspaceDivider()
                    SettingsToggleRow("Explain Changes", "Display reasoning notes with edits", showExplanation) { showExplanation = it }
                }
            }
            item {
                SettingsLabel("Environment Context")
                SettingsToggleGroup {
                    SettingsToggleRow("Auto-Detect Files", "Analyze workspace to retrieve context", autoDetectContext) { autoDetectContext = it }
                    WorkspaceDivider()
                    SettingsValueRow("Max Context Snapshots", "Limit count of project files analyzed", "20")
                    WorkspaceDivider()
                    SettingsValueRow("Ignore Directives", "Patterns to exclude from indexing", "3 patterns")
                }
            }
        }
    }
}

@Composable
private fun SettingsSubHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            color = AiTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        WorkspaceSectionTitle(title)
        GlassCard(accentColor = AiTheme.colors.aiPrimary) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text.uppercase(),
        style = MonoCaption,
        color = AiTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsBadge(text: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = AiTheme.colors.glassBase) {
        Text(
            text,
            style = MonoCaption,
            color = AiTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ThemeChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        accentColor = if (selected) AiTheme.colors.aiPrimary else Color.Transparent,
        modifier = modifier.height(100.dp).bounceClick(pressedScale = 0.98f, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = AiTheme.colors.textPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(8.dp))
                Text(title, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, style = MonoCaption, color = AiTheme.colors.textSecondary, textAlign = TextAlign.Center, maxLines = 1)
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = AiTheme.colors.aiPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) AiTheme.colors.aiPrimary.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (selected) AiTheme.colors.aiPrimary else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, AiTheme.colors.surfaceBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = if (color == Color.White) Color.Black else Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SettingsToggleGroup(content: @Composable () -> Unit) {
    GlassCard(accentColor = AiTheme.colors.aiPrimary) {
        Column(content = { content() })
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MonoCaption, color = AiTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    subtitle: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MonoCaption, color = AiTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MonoCaption, color = AiTheme.colors.textSecondary)
    }
}

@Composable
private fun CodePreviewPanel() {
    GlassCard(accentColor = AiTheme.colors.aiPrimary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AiTheme.colors.glassBase)
                .border(width = 1.dp, color = AiTheme.colors.surfaceBorder, shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Transparent)
                    .border(1.dp, AiTheme.colors.surfaceBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("preview.js", style = MonoCaption, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Row(modifier = Modifier.padding(12.dp)) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("1", "2", "3", "4").forEach { line ->
                    Text(line, style = MonoCaption, color = AiTheme.colors.textTertiary, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CodeLine("function workspace() {", Color.White)
                CodeLine("  const state = \"Monochrome IDE\";", AiTheme.colors.textSecondary)
                CodeLine("  return state;", Color.White)
                CodeLine("}", AiTheme.colors.textTertiary)
            }
        }
    }
}

@Composable
private fun CodeLine(text: String, color: Color) {
    Text(
        text,
        style = MonoCaption,
        color = color,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
