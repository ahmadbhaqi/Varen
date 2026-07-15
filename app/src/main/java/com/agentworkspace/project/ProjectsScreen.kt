package com.agentworkspace.project

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentworkspace.data.model.Project
import com.agentworkspace.shell.components.WorkspaceIconButton
import com.agentworkspace.shell.components.WorkspaceScreenBackground
import com.agentworkspace.shell.components.modern.EmptyStateModern
import com.agentworkspace.shell.components.modern.ProjectCardModern
import com.agentworkspace.shell.components.modern.QuickActionButton
import com.agentworkspace.shell.components.modern.SectionHeader
import com.agentworkspace.shell.presentation.projectsHeaderSubtitle
import com.agentworkspace.shell.theme.AiTheme
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.shell.theme.MonoCaption

@Composable
fun ProjectsScreen(
    onProjectClick: (String) -> Unit,
    onNewProject: () -> Unit,
    onOpenDrawer: () -> Unit,
    viewModel: ProjectViewModel = hiltViewModel(),
) {
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()

    WorkspaceScreenBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WorkspaceIconButton(
                        icon = Icons.Filled.Menu,
                        contentDescription = "Open navigation",
                        onClick = onOpenDrawer,
                    )
                    Spacer(Modifier.width(8.dp))
                    SectionHeader(
                        title = "Projects",
                        subtitle = projectsHeaderSubtitle(projects.size),
                        modifier = Modifier.weight(1f)
                    )
                    WorkspaceIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "Attach project",
                        onClick = onNewProject,
                        accent = AiTheme.colors.aiPrimary,
                    )
                }
            }

            if (projects.isEmpty()) {
                item { EmptyProjectsPanel(onNewProject) }
            } else {
                items(projects, key = { it.id }) { project ->
                    ProjectItem(project = project, onClick = { onProjectClick(project.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectsPanel(onNewProject: () -> Unit) {
    EmptyStateModern(
        icon = Icons.Filled.Folder,
        title = "No projects yet",
        subtitle = "Attach a folder or GitHub repository to start.",
        accent = AiTheme.colors.aiPrimary,
        action = {
            QuickActionButton(
                text = "Attach project",
                icon = Icons.Filled.Add,
                onClick = onNewProject,
                accentColor = AiTheme.colors.aiPrimary
            )
        }
    )
}

@Composable
private fun ProjectItem(project: Project, onClick: () -> Unit) {
    ProjectCardModern(
        title = project.name,
        subtitle = project.path,
        description = project.description.takeIf { it.isNotBlank() },
        status = project.trustMode.displayName.substringBefore(" "),
        statusColor = AiTheme.colors.aiPrimary,
        icon = Icons.Filled.Folder,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}
