package com.agentworkspace.shell.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
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
import com.agentworkspace.data.model.Project
import com.agentworkspace.shell.presentation.NavigationDestinationSpec
import com.agentworkspace.shell.presentation.formatCompactCount
import com.agentworkspace.shell.theme.AppTheme

@Composable
fun AdaptiveWorkspaceDrawer(
    destinations: List<NavigationDestinationSpec>,
    recentProjects: List<Project>,
    activeProjectId: String?,
    currentRoute: String?,
    connectionStatus: String,
    activeModelName: String,
    sessionTokens: Int,
    onHomeClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    onDestinationClick: (NavigationDestinationSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleRoutes = listOf(
        "Projects",
        "model_catalog",
        "Mcp",
        "Connections",
        "usage",
        "History",
        "Settings",
    )
    val visibleDestinations = visibleRoutes.mapNotNull { route ->
        destinations.firstOrNull { it.route == route }
    }

    Column(
        modifier = modifier
            .width(288.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AppTheme.colors.textPrimary,
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Varen",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textDisabled,
                )
            }
        }

        AdaptiveDrawerRow(
            icon = Icons.Filled.Add,
            title = "New conversation",
            selected = currentRoute == "Home",
            onClick = onHomeClick,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = AppTheme.colors.border,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (recentProjects.isNotEmpty()) {
                AdaptiveSectionHeader(
                    title = "RECENT",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                recentProjects.take(4).forEach { project ->
                    AdaptiveDrawerRow(
                        icon = Icons.Filled.Folder,
                        title = project.name,
                        selected = currentRoute == "project/{projectId}" && project.id == activeProjectId,
                        onClick = { onProjectClick(project.id) },
                    )
                }
                Spacer(Modifier.size(12.dp))
            }

            AdaptiveSectionHeader(
                title = "WORKSPACE",
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            visibleDestinations.forEach { destination ->
                AdaptiveDrawerRow(
                    icon = adaptiveDrawerIcon(destination.iconKey),
                    title = destination.title,
                    selected = currentRoute == destination.route,
                    onClick = { onDestinationClick(destination) },
                )
            }
        }

        HorizontalDivider(color = AppTheme.colors.border)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = activeModelName,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatCompactCount(sessionTokens)} session tokens",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textDisabled,
            )
        }
    }
}

private fun adaptiveDrawerIcon(iconKey: String): ImageVector = when (iconKey) {
    "projects" -> Icons.Filled.Folder
    "models" -> Icons.Filled.AutoAwesome
    "mcp" -> Icons.Filled.Palette
    "connections" -> Icons.Filled.Hub
    "usage" -> Icons.Filled.Analytics
    "history" -> Icons.Filled.History
    "settings" -> Icons.Filled.Settings
    else -> Icons.Filled.AutoAwesome
}
