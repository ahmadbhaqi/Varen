package com.agentworkspace.shell.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the AgentWorkspace.
 *
 * Organized around the workspace experience, NOT a provider dashboard.
 */
sealed interface Route {

    @Serializable
    data object Home : Route

    @Serializable
    data class ProjectDetail(val projectId: String) : Route

    @Serializable
    data class TaskDetail(val projectId: String, val taskId: String) : Route

    @Serializable
    data class DiffViewer(val projectId: String, val taskId: String, val diffId: String) : Route

    @Serializable
    data class History(val projectId: String? = null) : Route

    @Serializable
    data object Usage : Route

    @Serializable
    data object Connections : Route

    @Serializable
    data class ModelCatalog(val projectId: String? = null) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data class Editor(val projectId: String, val filePath: String) : Route

    @Serializable
    data object Mcp : Route
}
