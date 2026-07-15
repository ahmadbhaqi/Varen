package com.agentworkspace.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agentworkspace.shell.components.AdaptiveWorkspaceDrawer
import com.agentworkspace.shell.components.ProjectOverflowMenu
import com.agentworkspace.shell.navigation.NavGraph
import com.agentworkspace.shell.presentation.projectOverflowActions
import com.agentworkspace.shell.presentation.workspaceDrawerDestinations
import com.agentworkspace.shell.theme.AppTheme
import com.agentworkspace.workspace.home.HomeViewModel
import kotlinx.coroutines.launch

@Composable
fun AgentWorkspaceScaffold() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var projectMenuExpanded by remember { mutableStateOf(false) }
    var menuHasProject by remember { mutableStateOf(false) }
    val drawerDestinations = workspaceDrawerDestinations()
    val overflowActions = projectOverflowActions(menuHasProject)

    fun closeDrawer() {
        coroutineScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                drawerContentColor = AppTheme.colors.textPrimary,
            ) {
                WorkspaceDrawerStateContent(
                    destinations = drawerDestinations,
                    currentRoute = currentRoute,
                    onHomeClick = {
                        closeDrawer()
                        navController.navigate("Home") {
                            launchSingleTop = true
                            popUpTo("Home")
                        }
                    },
                    onProjectClick = { projectId ->
                        closeDrawer()
                        navController.navigate("project/$projectId") {
                            launchSingleTop = true
                        }
                    },
                    onDestinationClick = { destination ->
                        closeDrawer()
                        navController.navigate(destination.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavGraph(
                navController = navController,
                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                onOpenProjectMenu = { hasProject ->
                    menuHasProject = hasProject
                    projectMenuExpanded = true
                },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                ProjectOverflowMenu(
                    expanded = projectMenuExpanded,
                    actions = overflowActions,
                    onAction = { action ->
                        projectMenuExpanded = false
                        when (action.id) {
                            "workspace_settings" -> navController.navigate("Settings")
                            else -> navController.navigate("Projects")
                        }
                    },
                    onDismiss = { projectMenuExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceDrawerStateContent(
    destinations: List<com.agentworkspace.shell.presentation.NavigationDestinationSpec>,
    currentRoute: String?,
    onHomeClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    onDestinationClick: (com.agentworkspace.shell.presentation.NavigationDestinationSpec) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val homeState by viewModel.uiState.collectAsStateWithLifecycle()

    AdaptiveWorkspaceDrawer(
        destinations = destinations,
        recentProjects = homeState.recentProjects,
        activeProjectId = homeState.activeTaskProject?.id,
        currentRoute = currentRoute,
        connectionStatus = homeState.connectionStatus,
        activeModelName = homeState.activeModelName,
        sessionTokens = homeState.sessionTokens,
        onHomeClick = onHomeClick,
        onProjectClick = onProjectClick,
        onDestinationClick = onDestinationClick,
    )
}
