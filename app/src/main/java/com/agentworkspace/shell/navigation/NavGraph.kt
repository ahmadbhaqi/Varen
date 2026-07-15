package com.agentworkspace.shell.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.agentworkspace.settings.SettingsScreen
import com.agentworkspace.settings.connections.ConnectionStatusScreen
import com.agentworkspace.settings.connections.ConnectionsScreen
import com.agentworkspace.mcp.presentation.McpScreen
import com.agentworkspace.workspace.home.HomeScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    onOpenProjectMenu: (hasProject: Boolean) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = "Home",
    ) {
        composable("Home") {
            HomeScreen(
                onProjectClick = { projectId ->
                    navController.navigate("project/$projectId")
                },
                onNewProject = {
                    navController.navigate("project/new")
                },
                onTaskClick = { projectId, taskId ->
                    navController.navigate("project/$projectId/task/$taskId")
                },
                onConfigureModels = {
                    navController.navigate("Connections")
                },
                onOpenDrawer = onOpenDrawer,
                onProjectMenuClick = onOpenProjectMenu,
            )
        }

        composable("Projects") {
            com.agentworkspace.project.ProjectsScreen(
                onProjectClick = { projectId ->
                    navController.navigate("project/$projectId")
                },
                onNewProject = {
                    navController.navigate("project/new")
                },
                onOpenDrawer = onOpenDrawer,
            )
        }

        composable("History") {
            com.agentworkspace.history.HistoryScreen(
                onHistoryItemClick = { entry ->
                    val projectId = entry.projectId
                    val taskId = entry.taskId
                    if (projectId != null && taskId != null) {
                        navController.navigate("project/$projectId/task/$taskId")
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("usage") {
            com.agentworkspace.usage.UsageScreen(onOpenDrawer = onOpenDrawer)
        }

        composable("Settings") {
            SettingsScreen(
                onConnectionsClick = {
                    navController.navigate("Connections")
                },
                onOpenDrawer = onOpenDrawer,
            )
        }

        composable("Connections") {
            ConnectionsScreen(
                onConnectionClick = { connectionId ->
                    navController.navigate("connection/$connectionId")
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("ConnectionStatus") {
            ConnectionStatusScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("connection/{connectionId}") { backStackEntry ->
            ConnectionStatusScreen(
                connectionId = backStackEntry.arguments?.getString("connectionId"),
                onBack = { navController.popBackStack() },
            )
        }

        composable("project/{projectId}") { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            com.agentworkspace.project.ProjectDetailScreen(
                projectId = projectId,
                onTaskClick = { taskId ->
                    navController.navigate("project/$projectId/task/$taskId")
                },
                onFileClick = { filePath ->
                    navController.navigate("project/$projectId/editor/${Uri.encode(filePath)}")
                },
                onBack = { navController.popBackStack() },
                onStartTask = { taskId ->
                    navController.navigate("project/$projectId/task/$taskId")
                },
                onModelCatalog = {
                    navController.navigate("project/$projectId/models")
                },
            )
        }

        composable("project/new") {
            com.agentworkspace.project.CreateProjectScreen(
                onCreated = { projectId ->
                    navController.navigate("project/$projectId") {
                        popUpTo("Home")
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("project/{projectId}/task/{taskId}") { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
            com.agentworkspace.task.TaskDetailScreen(
                projectId = projectId,
                taskId = taskId,
                onDiffClick = {
                    navController.navigate("project/$projectId/task/$taskId/diff")
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("project/{projectId}/task/{taskId}/diff") { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
            com.agentworkspace.diff.DiffViewerScreen(
                projectId = projectId,
                taskId = taskId,
                onBack = { navController.popBackStack() },
            )
        }

        composable("project/{projectId}/editor/{filePath}") { backStackEntry ->
            val filePath = Uri.decode(backStackEntry.arguments?.getString("filePath") ?: return@composable)
            com.agentworkspace.editor.EditorScreen(
                filePath = filePath,
                onBack = { navController.popBackStack() },
            )
        }

        composable("model_catalog") {
            com.agentworkspace.model.catalog.ModelCatalogScreen(
                onBack = { navController.popBackStack() },
                showBack = false,
                onOpenDrawer = onOpenDrawer,
            )
        }

        composable("Mcp") {
            McpScreen(
                onOpenDrawer = onOpenDrawer,
            )
        }

        composable("project/{projectId}/models") { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            com.agentworkspace.model.catalog.ModelCatalogScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
