package com.agentworkspace.shell.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.animateColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import com.agentworkspace.shell.components.bounceClick
import com.agentworkspace.shell.theme.AiTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentworkspace.shell.presentation.ContextualAgentCardSpec
import com.agentworkspace.shell.presentation.ContextualCardKind
import com.agentworkspace.shell.presentation.NavigationDestinationSpec
import com.agentworkspace.shell.presentation.ProjectActionSpec
import com.agentworkspace.shell.presentation.WorkspaceContextSummary
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.workspace.home.HomeViewModel


data class WorkspaceStatusItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val accent: Color,
    val caption: String? = null,
    val progress: Float? = null,
)

data class WorkspaceMenuAction(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
    val accent: Color? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun WorkspaceScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}

@Composable
fun WorkspacePanel(
    modifier: Modifier = Modifier,
    accent: Color = AppTheme.colors.accent,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) AppTheme.colors.elevated else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.55f) else AppTheme.colors.border,
        ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun WorkspaceClickablePanel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = AppTheme.colors.accent,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) AppTheme.colors.elevated else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.55f) else AppTheme.colors.border,
        ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun WorkspaceBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.5f
            val top = h * 0.07f
            val upperMid = h * 0.36f
            val lowerMid = h * 0.64f
            val bottom = h * 0.93f
            val left = w * 0.11f
            val right = w * 0.89f

            val leftFace = Path().apply {
                moveTo(cx, top)
                lineTo(cx, upperMid)
                lineTo(left, h * 0.56f)
                lineTo(left, h * 0.27f)
                close()
            }
            val rightFace = Path().apply {
                moveTo(cx, top)
                lineTo(right, h * 0.27f)
                lineTo(right, h * 0.56f)
                lineTo(cx, upperMid)
                close()
            }
            val lowerFace = Path().apply {
                moveTo(left, h * 0.56f)
                lineTo(cx, lowerMid)
                lineTo(right, h * 0.56f)
                lineTo(cx, bottom)
                close()
            }
            val innerFace = Path().apply {
                moveTo(cx, upperMid)
                lineTo(right, h * 0.56f)
                lineTo(cx, lowerMid)
                lineTo(left, h * 0.56f)
                close()
            }

            drawPath(leftFace, Color(0xFFF5F5F5))
            drawPath(rightFace, Color(0xFFD4D4D4))
            drawPath(lowerFace, Color(0xFF737373))
            drawPath(innerFace, Color.White)
        }
    }
}

@Composable
fun WorkspaceHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onSubtitleClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading == null) {
            WorkspaceBrandMark(size = 32.dp)
        } else {
            leading()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Surface(
                onClick = { onSubtitleClick?.invoke() },
                enabled = onSubtitleClick != null,
                color = Color.Transparent,
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}

@Composable
fun WorkspaceIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AiTheme.colors.aiPrimary,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun WorkspaceStatusStrip(
    items: List<WorkspaceStatusItem>,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEachIndexed { index, item ->
                WorkspaceStatusCell(item = item, modifier = Modifier.weight(1f))
                if (index != items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(AppTheme.colors.border),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceStatusCell(
    item: WorkspaceStatusItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = AppTheme.colors.textPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                item.value,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.progress?.let { progress ->
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppTheme.colors.border),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(item.accent),
                )
            }
        }
    }
}

@Composable
fun WorkspaceSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun WorkspaceGroupedSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WorkspaceSectionTitle(title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, AppTheme.colors.border),
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun WorkspaceRowItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    accent: Color = AiTheme.colors.textSecondary,
    destructive: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = if (destructive) AiTheme.colors.aiError else AiTheme.colors.textPrimary
    val itemAccent = if (destructive) AiTheme.colors.aiError else accent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .bounceClick(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = itemAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Spacer(Modifier.height(1.dp))
                Text(it, style = MonoCaption, color = AiTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.let {
            Text(
                it,
                style = MonoCaption,
                color = AiTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 128.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AiTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun WorkspaceDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AiTheme.colors.surfaceBorder),
    )
}

@Composable
fun WorkspaceMenuContent(
    version: String,
    sections: List<Pair<String, List<WorkspaceMenuAction>>>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(AppTheme.colors.borderStrong)
                .align(Alignment.CenterHorizontally),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            WorkspaceBrandMark(size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Workspace", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                Text(version, style = MonoCaption, color = AppTheme.colors.textSecondary)
            }
            Surface(
                onClick = onClose,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, AppTheme.colors.border),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        sections.forEach { (title, actions) ->
            val primaryActions = actions.filterNot { it.destructive }
            val destructiveActions = actions.filter { it.destructive }
            if (primaryActions.isNotEmpty()) {
                WorkspaceGroupedSection(title = title) {
                    primaryActions.forEachIndexed { index, action ->
                        WorkspaceRowItem(
                            icon = action.icon,
                            title = action.title,
                            subtitle = action.subtitle,
                            trailing = action.trailing,
                            accent = AppTheme.colors.textSecondary,
                            destructive = action.destructive,
                            showChevron = !action.destructive,
                            onClick = action.onClick,
                        )
                        if (index != primaryActions.lastIndex) WorkspaceDivider()
                    }
                }
            }
            destructiveActions.forEach { action ->
                Surface(
                    onClick = action.onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
                ) {
                    WorkspaceRowItem(
                        icon = action.icon,
                        title = action.title,
                        subtitle = action.subtitle,
                        trailing = action.trailing,
                        accent = AppTheme.colors.textSecondary,
                        destructive = action.destructive,
                        showChevron = false,
                        onClick = action.onClick,
                    )
                }
            }
        }
    }
}

@Composable
fun WorkspaceTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AiTheme.colors.glassBase)
            .border(1.dp, AiTheme.colors.surfaceBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (active) AiTheme.colors.aiPrimary else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else AiTheme.colors.textSecondary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun WorkspaceSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
        },
        placeholder = {
            Text(placeholder, color = AppTheme.colors.textDisabled)
        },
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppTheme.colors.accent,
            unfocusedBorderColor = AppTheme.colors.border,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = AppTheme.colors.accent,
        ),
    )
}

@Composable
fun WorkspaceComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit,
) {
    WorkspacePanel(modifier = modifier, contentPadding = PaddingValues(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium) },
                maxLines = 6,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = AppTheme.colors.accent,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = leading)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
    }
}

@Composable
fun ConversationWorkspaceHeader(
    onOpenDrawer: () -> Unit,
    onOpenProjectMenu: () -> Unit,
    onProjectClick: (String) -> Unit,
    onNewProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val activeProject = uiState.activeTaskProject
    val recentProjects = uiState.recentProjects
    var dropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = AiTheme.colors.glassBase,
        border = BorderStroke(1.dp, AppTheme.colors.border),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkspaceIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = "Open navigation",
                onClick = onOpenDrawer,
            )
            Spacer(Modifier.width(12.dp))

            // Centered Inter-active Project Selector Dropdown
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { dropdownExpanded = true }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Column {
                    Text(
                        "WORKSPACE",
                        style = MonoCaption,
                        color = AiTheme.colors.aiPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeProject?.name ?: "Select Project",
                            style = MaterialTheme.typography.titleSmall,
                            color = AiTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            androidx.compose.material.icons.Icons.Filled.Tune,
                            contentDescription = null,
                            tint = AiTheme.colors.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    if (recentProjects.isNotEmpty()) {
                        Text(
                            text = "Switch Workspace",
                            style = MonoCaption,
                            color = AiTheme.colors.textTertiary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        recentProjects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.name) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = if (project.id == activeProject?.id) AiTheme.colors.aiPrimary else AiTheme.colors.textSecondary
                                    )
                                },
                                onClick = {
                                    dropdownExpanded = false
                                    onProjectClick(project.id)
                                }
                            )
                        }
                        androidx.compose.material3.HorizontalDivider(color = AiTheme.colors.surfaceBorder)
                    }
                    DropdownMenuItem(
                        text = { Text("New Workspace...") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = AiTheme.colors.aiPrimary
                            )
                        },
                        onClick = {
                            dropdownExpanded = false
                            onNewProject()
                        }
                    )
                }
            }

            WorkspaceIconButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = "Project actions",
                onClick = onOpenProjectMenu,
            )
        }
    }
}

@Composable
fun WorkspaceContextBar(
    summary: WorkspaceContextSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .bounceClick(pressedScale = 0.98f, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = AiTheme.colors.glassBase,
        border = BorderStroke(1.dp, AppTheme.colors.border),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AiTheme.colors.aiSecondary)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    summary.projectTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AiTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary.metaLine,
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun WorkspaceDrawerContent(
    destinations: List<NavigationDestinationSpec>,
    onDestinationClick: (NavigationDestinationSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val recentProjects = uiState.recentProjects
    val activeProject = uiState.activeTaskProject

    val allowedDrawerRoutes = listOf(
        "Projects",
        "model_catalog",
        "Mcp",
        "Connections",
        "usage",
        "History",
        "Settings",
    )
    val orderedDestinations = allowedDrawerRoutes.mapNotNull { route ->
        destinations.firstOrNull { destination ->
            destination.route == route && destination.title == drawerDestinationTitle(route)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.background)
            .border(BorderStroke(1.dp, AppTheme.colors.border), RoundedCornerShape(0.dp))
            .padding(start = 16.dp, top = 28.dp, end = 16.dp, bottom = 20.dp),
    ) {
        // Drawer Header with Neon Spark Gradient
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AiTheme.colors.glassBase),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "VAREN",
                    style = MaterialTheme.typography.titleMedium,
                    color = AiTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    "Android agent workspace",
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        WorkspaceDivider()
        Spacer(Modifier.height(14.dp))

        // Dynamic Workspace Status Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(14.dp),
            color = AiTheme.colors.glassBase,
            border = BorderStroke(1.dp, AiTheme.colors.surfaceBorder.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "SYSTEM STATE",
                        style = MonoCaption,
                        color = AiTheme.colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.connectionStatus == "Connected") AiTheme.colors.aiSuccess else AiTheme.colors.aiWarning
                                )
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            uiState.connectionStatus.uppercase(),
                            style = MonoCaption,
                            fontSize = 9.sp,
                            color = if (uiState.connectionStatus == "Connected") AiTheme.colors.aiSuccess else AiTheme.colors.aiWarning
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = activeProject?.name ?: "No active project",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AiTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Model: ${uiState.activeModelName}",
                    style = MonoCaption,
                    color = AiTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Scrollable Area for Projects & Modules
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recent Projects Subsection
            if (recentProjects.isNotEmpty()) {
                Column {
                    WorkspaceSectionTitle("Recent Workspaces")
                    Spacer(Modifier.height(6.dp))
                    recentProjects.take(3).forEach { project ->
                        val isCurrent = project.id == activeProject?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .let { rowModifier ->
                                    if (isCurrent) {
                                        rowModifier.background(AppTheme.colors.elevated)
                                    } else {
                                        rowModifier
                                    }
                                }
                                .clickable {
                                    onDestinationClick(
                                        NavigationDestinationSpec(
                                            route = "project/${project.id}",
                                            title = project.name,
                                            iconKey = "projects"
                                        )
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = if (isCurrent) AiTheme.colors.aiPrimary else AiTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) AiTheme.colors.textPrimary else AiTheme.colors.textSecondary,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Core Modules Section
            Column {
                WorkspaceSectionTitle("Workspace Modules")
                Spacer(Modifier.height(6.dp))
                orderedDestinations.forEach { destination ->
                    WorkspaceRowItem(
                        icon = drawerIcon(destination.iconKey),
                        title = destination.title,
                        accent = if (destination.route == "Projects") AiTheme.colors.aiPrimary else AiTheme.colors.textSecondary,
                        showChevron = false,
                        onClick = { onDestinationClick(destination) },
                    )
                }
            }
        }

        // Footer section
        WorkspaceDivider()
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Usage: ${uiState.sessionTokens} tokens",
                style = MonoCaption,
                color = AiTheme.colors.textTertiary
            )
            Text(
                "Trust: ${uiState.trustMode}",
                style = MonoCaption,
                color = AiTheme.colors.textTertiary
            )
        }
    }
}

@Composable
fun ProjectOverflowMenu(
    expanded: Boolean,
    actions: List<ProjectActionSpec>,
    onAction: (ProjectActionSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val allowedProjectActions = listOf(
        "workspace_settings",
    )
    val orderedActions = allowedProjectActions.mapNotNull { actionId ->
        actions.firstOrNull { action ->
            action.id == actionId && action.title == projectActionTitle(actionId)
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        orderedActions.forEach { action ->
            DropdownMenuItem(
                text = {
                    Text(
                        action.title,
                        color = AiTheme.colors.textPrimary,
                    )
                },
                leadingIcon = {
                    Icon(
                        if (action.destructive) Icons.Filled.DeleteOutline else Icons.Filled.Settings,
                        contentDescription = null,
                        tint = if (action.destructive) AiTheme.colors.aiError else AiTheme.colors.aiPrimary,
                    )
                },
                onClick = { onAction(action) },
            )
        }
    }
}

private fun drawerDestinationTitle(route: String): String = when (route) {
    "Projects" -> "Projects"
    "model_catalog" -> "Models"
    "Mcp" -> "UI Studio"
    "Connections" -> "Connections"
    "usage" -> "Usage"
    "History" -> "History"
    "Settings" -> "Settings"
    else -> route
}

private fun projectActionTitle(actionId: String): String = when (actionId) {
    "workspace_settings" -> "Workspace Settings"
    "rename" -> "Rename"
    "duplicate" -> "Duplicate"
    "export" -> "Export"
    "clear_conversation" -> "Clear Conversation"
    "delete_project" -> "Delete Project"
    else -> actionId
}

@Composable
fun ContextualAgentCard(
    card: ContextualAgentCardSpec,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(
        modifier = modifier,
        selected = !card.collapsedWhenComplete,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                contextualCardIcon(card.kind),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                card.title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        if (card.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                card.body,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (card.primaryAction != null || card.secondaryAction != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                card.secondaryAction?.let {
                    Surface(
                        onClick = onSecondaryAction,
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, AppTheme.colors.border),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
                card.primaryAction?.let {
                    Surface(
                        onClick = onPrimaryAction,
                        shape = MaterialTheme.shapes.small,
                        color = AppTheme.colors.accent,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.colors.textOnBrand,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgentComposer(
    value: String,
    modelLabel: String,
    isRunning: Boolean,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onModelClick: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aiPrimaryColor = AiTheme.colors.aiPrimary
    val aiSecondaryColor = AiTheme.colors.aiSecondary
    val surfaceBorderColor = AiTheme.colors.surfaceBorder
    val glassBaseColor = AiTheme.colors.glassBase
    val textTertiaryColor = AiTheme.colors.textTertiary
    val textPrimaryColor = AiTheme.colors.textPrimary
    val aiErrorColor = AiTheme.colors.aiError

    val isFocused = value.isNotEmpty()
    val transition = updateTransition(targetState = isFocused, label = "composerFocus")

    val colorStart by transition.animateColor(
        transitionSpec = { tween(400) },
        label = "colorStart"
    ) { focused ->
        if (focused) aiPrimaryColor else surfaceBorderColor.copy(alpha = 0.5f)
    }

    val colorEnd by transition.animateColor(
        transitionSpec = { tween(400) },
        label = "colorEnd"
    ) { focused ->
        if (focused) aiSecondaryColor else surfaceBorderColor.copy(alpha = 0.2f)
    }

    val elevation by transition.animateDp(
        transitionSpec = { tween(400) },
        label = "elevation"
    ) { focused ->
        if (focused) 6.dp else 2.dp
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = glassBaseColor,
        border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
        tonalElevation = elevation
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Message the agent...", color = AiTheme.colors.textTertiary) },
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AiTheme.colors.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = AiTheme.colors.aiPrimary,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onAttach,
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = AiTheme.colors.glassBase,
                    border = BorderStroke(1.dp, AiTheme.colors.surfaceBorder),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = "Attach",
                            tint = AiTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onModelClick,
                    shape = RoundedCornerShape(999.dp),
                    color = AiTheme.colors.glassBase,
                    border = BorderStroke(1.dp, AiTheme.colors.surfaceBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AiTheme.colors.aiPrimary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            modelLabel,
                            style = MonoCaption,
                            color = AiTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = if (isRunning) onStop else onSend,
                    enabled = isRunning || value.isNotBlank(),
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isRunning) AiTheme.colors.aiError else AiTheme.colors.aiPrimary,
                        contentColor = if (isRunning) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                        disabledContainerColor = AiTheme.colors.glassBase.copy(alpha = 0.5f),
                        disabledContentColor = AiTheme.colors.textTertiary,
                    ),
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isRunning) "Stop" else "Send",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun drawerIcon(iconKey: String): ImageVector = when (iconKey) {
    "projects" -> Icons.Filled.Folder
    "models" -> Icons.Filled.Tune
    "connections" -> Icons.Filled.Hub
    "usage" -> Icons.Filled.Analytics
    "history" -> Icons.Filled.History
    "mcp" -> Icons.Filled.Palette
    else -> Icons.Filled.Settings
}

private fun contextualCardIcon(kind: ContextualCardKind): ImageVector = when (kind) {
    ContextualCardKind.Planning -> Icons.Filled.Timeline
    ContextualCardKind.Execution -> Icons.Filled.Tune
    ContextualCardKind.Approval -> Icons.Filled.Check
    ContextualCardKind.Diff -> Icons.Filled.Description
    ContextualCardKind.Checkpoint -> Icons.Filled.Restore
    ContextualCardKind.Failure -> Icons.Filled.Close
    ContextualCardKind.Result -> Icons.Filled.Check
    ContextualCardKind.Usage -> Icons.Filled.Analytics
}
