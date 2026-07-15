package com.agentworkspace.settings.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ProviderAuthFlow
import com.agentworkspace.data.model.ProviderCategory
import com.agentworkspace.data.model.ProviderPreset
import com.agentworkspace.data.model.ProviderTransportFormat
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.components.WorkspaceSearchField
import com.agentworkspace.shell.components.WorkspaceSectionTitle
import com.agentworkspace.shell.presentation.connectionStatusLabel
import com.agentworkspace.shell.presentation.connectionTotals
import com.agentworkspace.shell.presentation.providerFilterLabels
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.components.modern.GlassCard
import com.agentworkspace.shell.theme.MonoCaption
import java.net.URI
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    onConnectionClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val testingId by viewModel.testingId.collectAsStateWithLifecycle()
    val totals = connectionTotals(connections)

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ConnectionsHeader(
                    title = "Connections",
                    subtitle = "Manage API providers and endpoints",
                    onBack = onBack,
                    trailing = {
                        WorkspaceIconButton(
                            icon = Icons.Filled.Add,
                            contentDescription = "Add connection",
                            onClick = { viewModel.showProviderPicker() },
                            accent = AiTheme.colors.aiPrimary,
                        )
                    },
                )
            }
            item {
                ConnectionTotalsStrip(totals)
            }
            item {
                Text("Your connections", style = MaterialTheme.typography.labelMedium, color = AiTheme.colors.textSecondary, fontWeight = FontWeight.SemiBold)
            }
            if (connections.isEmpty()) {
                item {
                    EmptyConnectionsState(onAdd = { viewModel.showProviderPicker() })
                }
            } else {
                items(connections, key = { it.id }) { connection ->
                    ConnectionListCard(
                        connection = connection,
                        isTesting = testingId == connection.id,
                        onClick = { onConnectionClick(connection.id) },
                        onToggle = { viewModel.toggleConnection(connection.id, !connection.isEnabled) },
                        onDelete = { viewModel.deleteConnection(connection.id) },
                        onTest = { viewModel.testConnection(connection) },
                        onAddModel = { viewModel.showManualModelDialog(connection) },
                    )
                }
            }
        }
    }

    ConnectionDialogs(viewModel)
}

@Composable
private fun ConnectionDialogs(viewModel: ConnectionsViewModel) {
    val showPicker by viewModel.showProviderPicker.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val showCustomDialog by viewModel.showCustomDialog.collectAsStateWithLifecycle()
    val customPreset by viewModel.customPreset.collectAsStateWithLifecycle()
    val manualModelConnection by viewModel.manualModelConnection.collectAsStateWithLifecycle()
    val deviceCodeState by viewModel.deviceCodeState.collectAsStateWithLifecycle()

    if (showPicker) {
        ProviderPickerSheet(
            presets = viewModel.presets,
            onPresetSelected = { viewModel.selectPreset(it) },
            onCustom = { viewModel.showCustomConnectionDialog() },
            onDismiss = { viewModel.dismissPicker() },
        )
    }

    selectedPreset?.let { preset ->
        ProviderAuthDialog(
            preset = preset,
            onApiKeySubmit = { apiKey -> viewModel.addConnectionWithApiKey(preset, apiKey) },
            onBrowserLogin = { viewModel.launchBrowserLogin(preset) },
            onDismiss = { viewModel.clearPreset() },
        )
    }

    if (showCustomDialog) {
        CustomConnectionDialog(
            preset = customPreset,
            onSubmit = { name, baseUrl, apiKey ->
                viewModel.addCustomConnection(customPreset, name, baseUrl, apiKey)
            },
            onDismiss = { viewModel.dismissCustomConnectionDialog() },
        )
    }

    deviceCodeState?.let { state ->
        DeviceCodeDialog(
            state = state,
            onOpen = { viewModel.openDeviceCodePage() },
            onDismiss = { viewModel.dismissDeviceCodeDialog() },
        )
    }

    manualModelConnection?.let { connection ->
        ManualModelDialog(
            connection = connection,
            onSubmit = { modelId, contextSize, vision, reasoning, toolUse, structured ->
                viewModel.addManualModel(
                    connection = connection,
                    modelId = modelId,
                    contextSize = contextSize,
                    vision = vision,
                    reasoning = reasoning,
                    toolUse = toolUse,
                    structuredOutput = structured,
                )
            },
            onDismiss = { viewModel.dismissManualModelDialog() },
        )
    }
}

@Composable
fun ConnectionStatusScreen(
    connectionId: String? = null,
    onBack: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val testingId by viewModel.testingId.collectAsStateWithLifecycle()
    val connection = remember(connections, connectionId) {
        connections.firstOrNull { it.id == connectionId } ?: connections.firstOrNull()
    }

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ConnectionsHeader(
                    title = "Connection Status",
                    subtitle = connection?.name ?: "No connection selected",
                    onBack = onBack,
                    centerTitle = true,
                    trailing = {},
                )
            }
            if (connection == null) {
                item {
                    EmptyConnectionsState(onAdd = { viewModel.showProviderPicker() })
                }
            } else {
                item { ConnectionHeroCard(connection) }
                item {
                    WorkspaceSectionTitle("Overview", modifier = Modifier.padding(top = 0.dp))
                    ConnectionServiceOverview(connection)
                }
                item {
                    WorkspaceSectionTitle("Services", modifier = Modifier.padding(top = 0.dp))
                    ServiceStatusPanel(connection)
                }
                item {
                    WorkspaceSectionTitle("Details", modifier = Modifier.padding(top = 0.dp))
                    ConnectionDetailsPanel(connection)
                }
                item {
                    ConnectionStatusActions(
                        isTesting = testingId == connection.id,
                        onTest = { viewModel.testConnection(connection) },
                        onDisconnect = {
                            viewModel.deleteConnection(connection.id)
                            onBack()
                        },
                    )
                }
            }
        }
    }

    ConnectionDialogs(viewModel)
}

@Composable
private fun ConnectionsHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    centerTitle: Boolean = false,
    trailing: @Composable () -> Unit,
) {
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
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (centerTitle) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = AiTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MonoCaption,
                color = AiTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centerTitle) TextAlign.Center else TextAlign.Start,
            )
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun ConnectionTotalsStrip(totals: com.agentworkspace.shell.presentation.ConnectionTotals) {
    WorkspacePanel(contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ConnectionTotalCell("Total", totals.total.toString(), AiTheme.colors.textPrimary, Modifier.weight(1f))
            StripDivider()
            ConnectionTotalCell("Enabled", totals.enabled.toString(), AiTheme.colors.aiPrimary, Modifier.weight(1f))
            StripDivider()
            ConnectionTotalCell("Ready", totals.ready.toString(), AiTheme.colors.aiSuccess, Modifier.weight(1f))
            StripDivider()
            ConnectionTotalCell("Error", totals.error.toString(), AiTheme.colors.aiError, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConnectionTotalCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MonoCaption, color = AiTheme.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun StripDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(AiTheme.colors.surfaceBorder),
    )
}

@Composable
private fun EmptyConnectionsState(onAdd: () -> Unit) {
    WorkspacePanel(contentPadding = PaddingValues(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProviderMark(name = "AI", accent = AiTheme.colors.aiPrimary, modifier = Modifier.size(48.dp))
            Text("No connections yet", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Add an API provider or local endpoint to make the agent ready for this workspace.",
                style = MaterialTheme.typography.bodySmall,
                color = AiTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onAdd,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiTheme.colors.aiPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add connection", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ConnectionListCard(
    connection: Connection,
    isTesting: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onAddModel: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val status = connection.connectionStatusLabel()
    val statusColor = connectionStatusColor(status)

    WorkspaceClickablePanel(
        onClick = onClick,
        accent = statusColor,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderMark(name = connection.name, accent = statusColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        connection.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AiTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    ConnectionBadge(text = connection.providerType.displayName, color = AiTheme.colors.aiPrimary)
                }
                Text(
                    connectionSecondaryLine(connection),
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Models: ${connection.models.size}",
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary.copy(alpha = 0.4f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(statusColor)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isTesting) "Testing" else status,
                        style = MonoCaption,
                        color = if (isTesting) AiTheme.colors.aiPrimary else statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Connection actions", tint = AiTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (connection.isEnabled) "Disable" else "Enable") },
                            leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onToggle()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Test Connection") },
                            leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onTest()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Add Model") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onAddModel()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Disconnect", color = AiTheme.colors.aiError) },
                            leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = AiTheme.colors.aiError, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPickerSheet(
    presets: List<ProviderPreset>,
    onPresetSelected: (ProviderPreset) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filters = remember(presets) { providerFilterLabels(presets) }
    val selectedCategory = remember(selectedFilter) {
        ProviderCategory.values().firstOrNull { it.displayName == selectedFilter }
    }
    val filtered = remember(presets, query, selectedCategory) {
        presets
            .filter { it.category != ProviderCategory.CUSTOM }
            .filter { preset ->
                (selectedCategory == null || preset.category == selectedCategory) &&
                    (query.isBlank() ||
                        preset.displayName.contains(query, ignoreCase = true) ||
                        preset.description.contains(query, ignoreCase = true) ||
                        preset.id.contains(query, ignoreCase = true) ||
                        preset.authFlow.displayName.contains(query, ignoreCase = true))
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = AiTheme.colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AiTheme.colors.surfaceBorder),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.94f)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorkspaceIconButton(Icons.Filled.Close, "Close", onDismiss)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Add Connection", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("Connect account or API provider", style = MonoCaption, color = AiTheme.colors.textSecondary)
                }
                Box(Modifier.size(36.dp))
            }
            WorkspaceSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search providers or endpoint",
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { label ->
                    FilterPill(
                        label = label,
                        selected = selectedFilter == label,
                        onClick = { selectedFilter = label },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProviderCategory.values()
                    .filter { it != ProviderCategory.CUSTOM }
                    .forEach { category ->
                        val categoryPresets = filtered.filter { it.category == category }
                        if (categoryPresets.isNotEmpty()) {
                            item(key = "section-${category.name}") {
                                Text(
                                    (if (category == ProviderCategory.OFFICIAL) "Popular Providers" else category.displayName).uppercase(),
                                    style = MonoCaption,
                                    color = AiTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            items(categoryPresets, key = { it.id }) { preset ->
                                ProviderPresetRow(preset = preset, onClick = { onPresetSelected(preset) })
                            }
                        }
                    }
                item {
                    Text("MORE OPTIONS", style = MonoCaption, color = AiTheme.colors.textSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
                item {
                    CustomProviderRow(onClick = onCustom)
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = if (selected) AiTheme.colors.glassBase else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) AiTheme.colors.aiPrimary else AiTheme.colors.surfaceBorder),
    ) {
        Text(
            label,
            style = MonoCaption,
            color = if (selected) AiTheme.colors.textPrimary else AiTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun ProviderPresetRow(preset: ProviderPreset, onClick: () -> Unit) {
    WorkspaceClickablePanel(
        onClick = onClick,
        accent = AiTheme.colors.aiPrimary,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderMark(name = preset.displayName, accent = AiTheme.colors.aiPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        preset.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AiTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    ConnectionBadge(text = preset.category.displayName, color = AiTheme.colors.aiPrimary)
                }
                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AiTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectionBadge(text = preset.authFlow.displayName, color = AiTheme.colors.aiPrimary)
                    ConnectionBadge(text = if (preset.supportsDirectRuntime) "Ready" else "Adapter", color = if (preset.supportsDirectRuntime) AiTheme.colors.aiSuccess else AiTheme.colors.aiWarning)
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, AiTheme.colors.surfaceBorder),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = "Add ${preset.displayName}", tint = AiTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CustomProviderRow(onClick: () -> Unit) {
    WorkspaceClickablePanel(
        onClick = onClick,
        accent = AiTheme.colors.aiPrimary,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderMark(name = "Custom", accent = AiTheme.colors.aiPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Custom Endpoint (API Key + Base URL)", style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                Text("Connect any OpenAI compatible service.", style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AiTheme.colors.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ConnectionHeroCard(connection: Connection) {
    val status = connection.connectionStatusLabel()
    WorkspacePanel(accent = connectionStatusColor(status), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderMark(name = connection.name, accent = connectionStatusColor(status), modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(connection.name, style = MaterialTheme.typography.bodyMedium, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    ConnectionBadge(text = connection.preset?.category?.displayName ?: "Direct", color = AiTheme.colors.aiPrimary)
                }
                Text(connectionSecondaryLine(connection), style = MonoCaption, color = AiTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status, style = MonoCaption, color = connectionStatusColor(status), fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                StatusDot(connectionStatusColor(status))
            }
        }
    }
}

@Composable
private fun ConnectionServiceOverview(connection: Connection) {
    val services = connectionServiceRows(connection)
    val healthy = services.count { it.status == "Healthy" }
    val warning = services.count { it.status == "Warning" }
    val disconnected = services.count { it.status == "Disconnected" }
    WorkspacePanel(contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ConnectionTotalCell("Healthy", healthy.toString(), AiTheme.colors.aiSuccess, Modifier.weight(1f))
            StripDivider()
            ConnectionTotalCell("Warning", warning.toString(), AiTheme.colors.aiWarning, Modifier.weight(1f))
            StripDivider()
            ConnectionTotalCell("Error", disconnected.toString(), AiTheme.colors.aiError, Modifier.weight(1f))
            StripDivider()
            ConnectionTotalCell("Total", services.size.toString(), AiTheme.colors.textPrimary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ServiceStatusPanel(connection: Connection) {
    WorkspacePanel(contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
        connectionServiceRows(connection).forEachIndexed { index, row ->
            ServiceStatusRow(row)
            if (index != connectionServiceRows(connection).lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(AiTheme.colors.surfaceBorder))
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(row: ConnectionServiceRow) {
    val color = serviceStatusColor(row.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Text(row.status.uppercase(), style = MonoCaption, color = AiTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        StatusDot(color)
    }
}

@Composable
private fun ConnectionDetailsPanel(connection: Connection) {
    WorkspacePanel(contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
        DetailRow(Icons.Filled.Key, "Type", connectionAuthTypeLabel(connection))
        DetailDivider()
        DetailRow(Icons.Filled.Link, "Base URL", connection.baseUrl?.ifBlank { "Not set" } ?: "Not set")
        DetailDivider()
        DetailRow(Icons.Filled.Memory, "Models Available", connection.models.size.toString())
        DetailDivider()
        DetailRow(Icons.Filled.Sync, "Last Sync", connection.lastHealthCheck?.let { formatConnectionTime(it) } ?: "Not tested")
        DetailDivider()
        DetailRow(Icons.Filled.Security, "Status", connection.connectionStatusLabel())
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AiTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MonoCaption, color = AiTheme.colors.textSecondary, modifier = Modifier.weight(0.8f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = AiTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun DetailDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AiTheme.colors.surfaceBorder))
}

@Composable
private fun ConnectionStatusActions(
    isTesting: Boolean,
    onTest: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onTest,
            enabled = !isTesting,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = AiTheme.colors.aiPrimary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isTesting) "Testing Connection" else "Test Connection", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onDisconnect,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, AiTheme.colors.aiError.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = AiTheme.colors.aiError, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Disconnect", color = AiTheme.colors.aiError)
        }
    }
}

@Composable
private fun ProviderMark(
    name: String,
    accent: Color,
    modifier: Modifier = Modifier.size(36.dp),
) {
    com.agentworkspace.shell.components.ProviderLogo(providerName = name, modifier = modifier)
}

@Composable
private fun ConnectionBadge(text: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = color.copy(alpha = 0.10f), border = BorderStroke(1.dp, color.copy(alpha = 0.28f))) {
        Text(
            text,
            style = MonoCaption,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun connectionStatusColor(status: String): Color = when (status) {
    "Ready", "Healthy" -> AiTheme.colors.aiSuccess
    "Warning" -> AiTheme.colors.aiWarning
    else -> AiTheme.colors.aiError
}

@Composable
private fun serviceStatusColor(status: String): Color = when (status) {
    "Healthy" -> AiTheme.colors.aiSuccess
    "Warning" -> AiTheme.colors.aiWarning
    else -> AiTheme.colors.aiError
}

private data class ConnectionServiceRow(
    val label: String,
    val status: String,
)

private fun connectionServiceRows(connection: Connection): List<ConnectionServiceRow> {
    val authStatus = when (connection.authState) {
        AuthState.AUTHENTICATED -> "Healthy"
        AuthState.EXPIRED -> "Warning"
        AuthState.ERROR -> "Disconnected"
        AuthState.NOT_AUTHENTICATED -> if (connection.effectiveAuthKey.isNullOrBlank()) "Warning" else "Healthy"
    }
    val modelStatus = if (connection.models.isNotEmpty()) "Healthy" else "Warning"
    val runtimeStatus = if (connection.preset?.supportsDirectRuntime != false) "Healthy" else "Warning"
    val endpointStatus = if (!connection.baseUrl.isNullOrBlank()) "Healthy" else "Warning"
    val enabledStatus = if (connection.isEnabled) "Healthy" else "Disconnected"

    return listOf(
        ConnectionServiceRow("API Authentication", authStatus),
        ConnectionServiceRow("Model Access", modelStatus),
        ConnectionServiceRow("Runtime Adapter", runtimeStatus),
        ConnectionServiceRow("Endpoint", endpointStatus),
        ConnectionServiceRow("Enabled State", enabledStatus),
    )
}

private fun connectionSecondaryLine(connection: Connection): String {
    val auth = connectionAuthTypeLabel(connection)
    val endpoint = endpointHost(connection.baseUrl)
    return if (endpoint.isBlank()) auth else "$endpoint - $auth"
}

private fun connectionAuthTypeLabel(connection: Connection): String =
    connection.preset?.authFlow?.displayName
        ?: if (!connection.effectiveAuthKey.isNullOrBlank()) "API key" else connection.authScheme.headerName.ifBlank { "Credential" }

private fun endpointHost(url: String?): String {
    val value = url?.trim().orEmpty()
    if (value.isBlank()) return ""
    return runCatching {
        URI(value).host?.removePrefix("www.") ?: value.removePrefix("https://").removePrefix("http://")
    }.getOrElse { value }
}

private fun providerInitials(name: String): String {
    val clean = name.replace("(", " ").replace(")", " ").replace("-", " ")
    val words = clean.split(" ").filter { it.isNotBlank() }
    return when {
        name.contains("OpenAI", ignoreCase = true) -> "OA"
        name.contains("Anthropic", ignoreCase = true) -> "AI"
        name.contains("Gemini", ignoreCase = true) -> "G"
        name.contains("Groq", ignoreCase = true) -> "g"
        name.contains("OpenRouter", ignoreCase = true) -> "OR"
        words.size >= 2 -> words.take(2).joinToString("") { it.first().uppercaseChar().toString() }
        else -> name.take(2).uppercase(Locale.US)
    }
}

private fun formatConnectionTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

@Composable
private fun ProviderAuthDialog(
    preset: ProviderPreset,
    onApiKeySubmit: (String) -> Unit,
    onBrowserLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    val needsTypedCredential = preset.authFlow == ProviderAuthFlow.API_KEY ||
        preset.authFlow == ProviderAuthFlow.BROWSER_TOKEN ||
        preset.authFlow == ProviderAuthFlow.COOKIE ||
        (!preset.supportsBrowserLogin && preset.authFlow != ProviderAuthFlow.NO_AUTH)
    val credentialLabel = when (preset.authFlow) {
        ProviderAuthFlow.API_KEY -> "API Key"
        ProviderAuthFlow.BROWSER_TOKEN -> "Access token"
        ProviderAuthFlow.COOKIE -> "Session cookie"
        else -> "Credential"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to ${preset.displayName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(preset.description, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textSecondary)
                if (!preset.supportsDirectRuntime) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${preset.transportFormat.displayName} is registered in the catalog. Direct execution depends on its runtime adapter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AiTheme.colors.textSecondary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                if (preset.authFlow == ProviderAuthFlow.NO_AUTH) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "No credential is required. The app will connect directly to the configured endpoint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AiTheme.colors.textSecondary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                } else if (needsTypedCredential) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(credentialLabel) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = AiTheme.colors.textPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${preset.authFlow.displayName} uses your provider account quota instead of an API key.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AiTheme.colors.textSecondary,
                            )
                        }
                    }
                }
                if (preset.supportsBrowserLogin) {
                    Surface(
                        onClick = onBrowserLogin,
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = AiTheme.colors.textPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (preset.authFlow) {
                                    ProviderAuthFlow.API_KEY -> "Open API Key Page"
                                    ProviderAuthFlow.DEVICE_CODE -> "Start Device Login"
                                    ProviderAuthFlow.NO_AUTH -> "Open Provider Page"
                                    else -> "Open Browser Login"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = AiTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (needsTypedCredential || preset.authFlow == ProviderAuthFlow.NO_AUTH) {
                TextButton(
                    onClick = { onApiKeySubmit(apiKey) },
                    enabled = apiKey.isNotBlank() || preset.authFlow == ProviderAuthFlow.NO_AUTH,
                ) {
                    Text(
                        when (preset.authFlow) {
                            ProviderAuthFlow.NO_AUTH -> "Connect"
                            ProviderAuthFlow.BROWSER_TOKEN, ProviderAuthFlow.COOKIE -> "Save token"
                            else -> "Save key"
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                TextButton(onClick = onBrowserLogin) {
                    Text(
                        if (preset.authFlow == ProviderAuthFlow.DEVICE_CODE) "Start login" else "Browser login",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AiTheme.colors.textSecondary) } },
    )
}

@Composable
private fun DeviceCodeDialog(
    state: DeviceCodeUiState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${state.providerName} login", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.status, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textSecondary)
                if (state.userCode.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Code", style = MonoCaption, color = AiTheme.colors.textSecondary)
                            Text(
                                state.userCode,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AiTheme.colors.textPrimary,
                            )
                        }
                    }
                }
                Text(
                    state.verificationUri,
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = if (state.isComplete) onDismiss else onOpen) {
                Text(
                    if (state.isComplete) "Done" else "Open browser",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        dismissButton = {
            if (!state.isComplete) TextButton(onClick = onDismiss) { Text("Hide", color = AiTheme.colors.textSecondary) }
        },
    )
}

@Composable
private fun CustomConnectionDialog(
    preset: ProviderPreset,
    onSubmit: (name: String, baseUrl: String, apiKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(preset.id) { mutableStateOf(preset.displayName) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    val endpointHint = if (preset.transportFormat == ProviderTransportFormat.CLAUDE_NATIVE) {
        "Use a Claude/Anthropic-compatible messages endpoint, for example https://api.example.com/v1/messages."
    } else {
        "Use an OpenAI-compatible endpoint. Paste a base path such as https://api.example.com/v1/ or a full /chat/completions URL."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preset.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(endpointHint, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textSecondary)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (preset.supportsApiKey) "API Key" else "Credential") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name.trim(), baseUrl.trim(), apiKey.trim()) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
            ) {
                Text("Connect", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AiTheme.colors.textSecondary) } },
    )
}

@Composable
private fun ManualModelDialog(
    connection: Connection,
    onSubmit: (
        modelId: String,
        contextSize: Int?,
        vision: Boolean,
        reasoning: Boolean,
        toolUse: Boolean,
        structuredOutput: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var modelId by remember(connection.id) { mutableStateOf("") }
    var contextSizeText by remember(connection.id) { mutableStateOf("") }
    var vision by remember(connection.id) { mutableStateOf(false) }
    var reasoning by remember(connection.id) { mutableStateOf(false) }
    var toolUse by remember(connection.id) { mutableStateOf(true) }
    var structured by remember(connection.id) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Use this when model discovery fails. Enter the exact model id expected by ${connection.name}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AiTheme.colors.textSecondary,
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    placeholder = { Text("provider/model-name or model-name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contextSizeText,
                    onValueChange = { value -> contextSizeText = value.filter { it.isDigit() } },
                    label = { Text("Context tokens") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CapabilityToggle("Vision", vision) { vision = it }
                CapabilityToggle("Reasoning", reasoning) { reasoning = it }
                CapabilityToggle("Tools / function calls", toolUse) { toolUse = it }
                CapabilityToggle("Structured output", structured) { structured = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        modelId.trim(),
                        contextSizeText.toIntOrNull(),
                        vision,
                        reasoning,
                        toolUse,
                        structured,
                    )
                },
                enabled = modelId.isNotBlank(),
            ) {
                Text("Add", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AiTheme.colors.textSecondary) } },
    )
}

@Composable
private fun CapabilityToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = AiTheme.colors.textPrimary)
    }
}


@Composable
private fun WorkspacePanel(
    modifier: Modifier = Modifier,
    accent: Color = AiTheme.colors.aiPrimary,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = if (selected) accent else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
private fun WorkspaceClickablePanel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = AiTheme.colors.aiPrimary,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = if (selected) accent else Color.Transparent,
        onClick = if (enabled) onClick else null
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
