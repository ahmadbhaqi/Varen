package com.agentworkspace.model.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.AvailabilityState
import com.agentworkspace.data.model.ModelCapabilities
import com.agentworkspace.shell.components.CapabilityChip
import com.agentworkspace.shell.components.WorkspaceClickablePanel
import com.agentworkspace.shell.components.WorkspaceHeader
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.WorkspacePanel
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.components.WorkspaceSearchField
import com.agentworkspace.shell.presentation.modelCatalogHeaderTitle
import com.agentworkspace.shell.presentation.modelCatalogSubtitle
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun ModelCatalogScreen(
    onBack: () -> Unit,
    projectId: String? = null,
    showBack: Boolean = true,
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: ModelCatalogViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val activeProjectId by viewModel.activeProjectId.collectAsStateWithLifecycle(null)
    val selectionProjectId = resolveSelectionProjectId(projectId, activeProjectId)
    val selectedModelIdFlow = remember(selectionProjectId) {
        if (selectionProjectId != null) viewModel.selectedModelId(selectionProjectId) else MutableStateFlow<String?>(null)
    }
    val selectedModelId by selectedModelIdFlow.collectAsStateWithLifecycle(null)
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableIntStateOf(0) }
    val filters = remember { listOf("All", "Reasoning", "Fast", "Balanced", "Vision", "...") }
    val filtered = remember(rows, query, selectedFilter) {
        rows.filter {
            val matchesQuery = query.isBlank() ||
                it.model.name.contains(query, ignoreCase = true) ||
                it.connectionName.contains(query, ignoreCase = true)
            val matchesFilter = when (filters[selectedFilter]) {
                "Reasoning" -> it.model.capabilities.reasoning
                "Fast" -> it.model.capabilities.streaming
                "Balanced" -> it.model.isRecommended || it.model.capabilities.toolUse || it.model.capabilities.functionCalling
                "Vision" -> it.model.capabilities.vision
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }
    val selectedOrRecommended = filtered
        .filter { row -> isSelectedModel(row.model.id, selectedModelId, row.model.isRecommended) }
        .ifEmpty { filtered.take(1) }
    val selectedIds = selectedOrRecommended.map { it.model.id }.toSet()
    val remainingRows = filtered.filterNot { it.model.id in selectedIds }
    val recentRows = remainingRows.take(3)
    val allRows = remainingRows.drop(3)

    fun select(row: ModelRow) {
        if (selectionProjectId != null) viewModel.useModelForProject(selectionProjectId, row.model)
    }

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WorkspaceHeader(
                    title = modelCatalogHeaderTitle(),
                    subtitle = modelCatalogSubtitle(selectionProjectId),
                    leading = {
                        if (showBack) {
                            WorkspaceIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                        } else if (onOpenDrawer != null) {
                            WorkspaceIconButton(Icons.Filled.Menu, "Open navigation", onOpenDrawer)
                        } else {
                            Spacer(Modifier.size(38.dp))
                        }
                    },
                ) {
                    if (showBack) {
                        WorkspaceIconButton(Icons.Filled.Close, "Close", onBack)
                    }
                }
            }

            item {
                WorkspaceSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search model catalog...",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters.size) { index ->
                        FilterChip(
                            label = filters[index],
                            selected = selectedFilter == index,
                            onClick = { selectedFilter = index },
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    WorkspacePanel(accent = AppTheme.colors.warning, selected = true) {
                        Text("No models available", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (rows.isEmpty()) "Connect a provider in Settings to discover models." else "Try another search or filter.",
                            style = MonoCaption,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                }
            } else {
                item {
                    SectionLabel("Recommended")
                }
                items(selectedOrRecommended, key = { "recommended-${it.model.id}" }) { row ->
                    val selected = isSelectedModel(row.model.id, selectedModelId, row.model.isRecommended)
                    ModelRowCard(
                        row = row,
                        selected = selected,
                        selectable = isModelSelectable(selectionProjectId, row.model.availabilityState),
                        prominent = true,
                        onClick = { select(row) },
                    )
                }
                if (recentRows.isNotEmpty()) {
                    item { SectionLabel("Recent") }
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                        ) {
                            Column {
                                recentRows.forEachIndexed { index, row ->
                                    val selected = row.model.id == selectedModelId
                                    CompactModelRow(
                                        row = row,
                                        selected = selected,
                                        selectable = isModelSelectable(selectionProjectId, row.model.availabilityState),
                                        onClick = { select(row) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (allRows.isNotEmpty()) {
                    item { SectionLabel("All Models") }
                    items(allRows, key = { "all-${it.model.id}" }) { row ->
                        val selected = row.model.id == selectedModelId
                        ModelRowCard(
                            row = row,
                            selected = selected,
                            selectable = isModelSelectable(selectionProjectId, row.model.availabilityState),
                            prominent = false,
                            onClick = { select(row) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRowCard(
    row: ModelRow,
    selected: Boolean,
    selectable: Boolean,
    prominent: Boolean,
    onClick: () -> Unit,
) {
    val model = row.model
    val accent = availabilityColor(model.availabilityState)
    WorkspaceClickablePanel(
        onClick = onClick,
        enabled = selectable && model.availabilityState == AvailabilityState.AVAILABLE,
        accent = if (selected) AppTheme.colors.accent else accent,
        selected = selected,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderMark(model.name, accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(modelSubtitle(row), style = MonoCaption, color = AppTheme.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val chips = capabilityChips(model.capabilities)
                if (prominent && chips.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        chips.take(2).forEach { (label, color) ->
                            CapabilityChip(text = label, color = color)
                        }
                        model.contextSize?.let { CapabilityChip(text = contextLabel(it), color = AppTheme.colors.textSecondary) }
                    }
                }
            }
            if (selected) {
                Surface(
                    onClick = onClick,
                    enabled = selectable,
                    shape = CircleShape,
                    color = AppTheme.colors.accent,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = AppTheme.colors.textOnBrand, modifier = Modifier.size(14.dp))
                    }
                }
            } else {
                Surface(
                    onClick = onClick,
                    enabled = selectable,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = "Select", tint = if (selectable) AppTheme.colors.textSecondary else AppTheme.colors.textDisabled, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactModelRow(
    row: ModelRow,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    val accent = availabilityColor(row.model.availabilityState)
    Surface(onClick = onClick, enabled = selectable, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderMark(row.model.name, accent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.model.name, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(modelSubtitle(row), style = MonoCaption, color = AppTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(row.model.contextSize?.let { contextLabel(it) } ?: availabilityLabel(row.model.availabilityState), style = MonoCaption, color = AppTheme.colors.textSecondary)
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Surface(onClick = onClick, enabled = selectable, shape = CircleShape, color = AppTheme.colors.accent) {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = AppTheme.colors.textOnBrand, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderMark(name: String, accent: Color) {
    ProviderMark(name = name, accent = accent, modifier = Modifier.size(42.dp))
}

@Composable
private fun ProviderMark(name: String, accent: Color, modifier: Modifier) {
    com.agentworkspace.shell.components.ProviderLogo(providerName = name, modifier = modifier)
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) AppTheme.colors.accentSoft else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AppTheme.colors.accent else AppTheme.colors.border),
    ) {
        val content = if (label == "...") {
            Icons.Filled.MoreHoriz
        } else {
            null
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (content != null) {
                Icon(content, contentDescription = label, tint = if (selected) AppTheme.colors.accent else AppTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
            } else {
                Text(label, style = MonoCaption, color = if (selected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Memory, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun capabilityChips(c: ModelCapabilities): List<Pair<String, Color>> = buildList {
    if (c.toolUse || c.functionCalling) add("Tools" to AppTheme.colors.accent)
    if (c.vision) add("Vision" to AppTheme.colors.info)
    if (c.reasoning) add("Reasoning" to AppTheme.colors.warning)
    if (c.structuredOutput) add("Structured" to AppTheme.colors.accent)
    if (c.codeAssistance) add("Code" to AppTheme.colors.success)
}

@Composable
private fun availabilityColor(state: AvailabilityState) = when (state) {
    AvailabilityState.AVAILABLE -> AppTheme.colors.success
    AvailabilityState.UNAVAILABLE -> AppTheme.colors.textDisabled
    AvailabilityState.UNKNOWN -> AppTheme.colors.textDisabled
}

private fun availabilityLabel(state: AvailabilityState) = when (state) {
    AvailabilityState.AVAILABLE -> "available"
    AvailabilityState.UNAVAILABLE -> "unavailable"
    AvailabilityState.UNKNOWN -> "unknown"
}

private fun modelSubtitle(row: ModelRow): String =
    row.model.recommendationReason ?: "by ${row.connectionName}"

private fun contextLabel(size: Int): String = when {
    size >= 1_000_000 -> "${size / 1_000_000}M"
    size >= 1_000 -> "${size / 1_000}K"
    else -> size.toString()
}
