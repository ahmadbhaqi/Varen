package com.agentworkspace.shell.presentation

import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.AgentStep
import com.agentworkspace.data.model.AvailabilityState
import com.agentworkspace.data.model.Checkpoint
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ModelCapabilities
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.data.model.ProviderCategory
import com.agentworkspace.data.model.ProviderPreset
import java.util.Locale
import kotlin.math.roundToInt

enum class HomePromptIcon {
    Continue,
    Review,
    Project,
    Checkpoint,
}

data class HomePromptSuggestion(
    val title: String,
    val prompt: String,
    val icon: HomePromptIcon,
)

data class HomeEmptyConversationSpec(
    val primaryAction: String,
    val body: String,
    val suggestion: String,
)

enum class WorkspaceConversationMode {
    Empty,
    Active,
}

data class HomeModelOption(
    val id: String,
    val connectionId: String,
    val name: String,
    val detail: String,
    val enabled: Boolean,
    val selected: Boolean,
)

data class CheckpointTotals(
    val total: Int,
    val automatic: Int,
    val manual: Int,
)

data class CheckpointChatCardSpec(
    val title: String,
    val body: String,
    val meta: String,
    val actionLabel: String,
)

data class AgentRunPresentation(
    val label: String,
    val headline: String,
    val detail: String,
    val currentStep: Int,
    val totalSteps: Int,
    val isTerminal: Boolean,
)

data class ConnectionTotals(
    val total: Int,
    val enabled: Int,
    val ready: Int,
    val error: Int,
)

data class WorkspaceContextSummary(
    val projectTitle: String,
    val metaLine: String,
    val hasProject: Boolean,
)

sealed class HomeConversationSendTarget {
    data object None : HomeConversationSendTarget()
    data class ContinueWithoutProject(val prompt: String) : HomeConversationSendTarget()
    data class StartProjectTask(val projectId: String, val prompt: String) : HomeConversationSendTarget()
}

sealed class HomePendingPromptAction {
    data object None : HomePendingPromptAction()
    data class AttachProject(val prompt: String) : HomePendingPromptAction()
    data class RunInProject(val projectId: String, val prompt: String) : HomePendingPromptAction()
}

data class NavigationDestinationSpec(
    val route: String,
    val title: String,
    val iconKey: String,
)

data class ProjectActionSpec(
    val id: String,
    val title: String,
    val destructive: Boolean = false,
)

enum class ContextualCardKind {
    Planning,
    Execution,
    Approval,
    Diff,
    Checkpoint,
    Failure,
    Result,
    Usage,
}

data class ContextualAgentCardSpec(
    val kind: ContextualCardKind,
    val title: String,
    val body: String,
    val primaryAction: String? = null,
    val secondaryAction: String? = null,
    val collapsedWhenComplete: Boolean = true,
)

fun formatCompactCount(value: Int): String = when {
    value >= 1_000_000 -> "${((value / 100_000.0).roundToInt() / 10.0).trimOneDecimal()}M"
    value >= 1_000 -> "${((value / 100.0).roundToInt() / 10.0).trimOneDecimal()}K"
    else -> value.coerceAtLeast(0).toString()
}

fun formatUsageToday(tokens: Int): String = formatCompactCount(tokens)

fun homeConversationMode(savedPrompt: String?, activeTask: Task?): WorkspaceConversationMode =
    if (savedPrompt.isNullOrBlank() && activeTask == null) {
        WorkspaceConversationMode.Empty
    } else {
        WorkspaceConversationMode.Active
    }

fun homeConversationTasks(activeTasks: List<Task>, latestTerminalTask: Task?): List<Task> =
    activeTasks.ifEmpty { listOfNotNull(latestTerminalTask) }

fun modelCapabilityLabel(capabilities: ModelCapabilities): String = when {
    capabilities.reasoning && capabilities.toolUse -> "Reasoning · tools"
    capabilities.reasoning -> "Reasoning"
    capabilities.vision -> "Vision"
    capabilities.streaming -> "Fast responses"
    else -> "General"
}

fun homeModelOptions(
    models: List<ModelInfo>,
    selectedModelId: String?,
    hasProject: Boolean,
): List<HomeModelOption> = models
    .map { model ->
        HomeModelOption(
            id = model.id,
            connectionId = model.connectionId,
            name = model.name,
            detail = when {
                !hasProject -> "Attach a project to select"
                model.availabilityState == AvailabilityState.UNAVAILABLE -> "Unavailable"
                model.availabilityState == AvailabilityState.UNKNOWN -> "Availability unknown"
                else -> modelCapabilityLabel(model.capabilities)
            },
            enabled = hasProject && model.availabilityState == AvailabilityState.AVAILABLE,
            selected = model.id == selectedModelId,
        )
    }
    .sortedWith(compareByDescending<HomeModelOption> { it.selected }.thenBy { it.name })

fun formatProgressPercent(current: Int, total: Int): Int {
    if (total <= 0) return 0
    return ((current.coerceAtLeast(0).toFloat() / total) * 100).roundToInt().coerceIn(0, 100)
}

fun agentRunPresentation(
    status: TaskStatus?,
    plan: List<AgentStep>,
    currentStepIndex: Int,
): AgentRunPresentation {
    val totalSteps = plan.size
    val safeIndex = if (totalSteps == 0) 0 else currentStepIndex.coerceIn(0, totalSteps - 1)
    val currentStep = if (totalSteps == 0) 0 else safeIndex + 1
    val activeStep = plan.getOrNull(safeIndex)?.description
    val executionDetail = if (totalSteps > 0) {
        "Step $currentStep of $totalSteps"
    } else {
        "The agent is executing the task."
    }

    return when (status) {
        TaskStatus.QUEUED, null -> AgentRunPresentation(
            label = "Queued",
            headline = "Preparing task",
            detail = "Waiting for the agent to begin.",
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = false,
        )
        TaskStatus.PLANNING -> AgentRunPresentation(
            label = "Planning",
            headline = "Preparing an execution plan",
            detail = "The agent is understanding the task before it makes changes.",
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = false,
        )
        TaskStatus.READING_CONTEXT -> AgentRunPresentation(
            label = "Reading context",
            headline = activeStep ?: "Inspecting the workspace",
            detail = executionDetail,
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = false,
        )
        TaskStatus.EDITING,
        TaskStatus.RUNNING_LOOP,
        TaskStatus.EXECUTING,
        TaskStatus.RETRYING -> AgentRunPresentation(
            label = status.displayName,
            headline = activeStep ?: "Working on your task",
            detail = executionDetail,
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = false,
        )
        TaskStatus.VERIFYING -> AgentRunPresentation(
            label = "Verifying",
            headline = activeStep ?: "Checking the result",
            detail = executionDetail,
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = false,
        )
        TaskStatus.WAITING_APPROVAL -> AgentRunPresentation(
            label = "Approval needed",
            headline = "Review the proposed action",
            detail = "The agent is waiting for your decision.",
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = false,
        )
        TaskStatus.COMPLETED -> AgentRunPresentation(
            label = "Completed",
            headline = "Task complete",
            detail = "Review the activity and changed files below.",
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = true,
        )
        TaskStatus.FAILED -> AgentRunPresentation(
            label = "Needs attention",
            headline = "Task did not complete",
            detail = "Review the activity log for the failure details.",
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = true,
        )
        TaskStatus.ROLLED_BACK -> AgentRunPresentation(
            label = "Rolled back",
            headline = "Changes were restored",
            detail = "Review the activity log before continuing.",
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = true,
        )
        TaskStatus.PAUSED -> AgentRunPresentation(
            label = "Paused",
            headline = "Task is paused",
            detail = "Send another instruction when you are ready to continue.",
            currentStep = currentStep,
            totalSteps = totalSteps,
            isTerminal = false,
        )
    }
}

fun connectionHealthLabel(status: String): String = when (status.lowercase()) {
    "connected", "healthy" -> "Healthy"
    "auth needed", "needs auth" -> "Needs Auth"
    else -> "Offline"
}

fun connectionTotals(connections: List<Connection>): ConnectionTotals =
    ConnectionTotals(
        total = connections.size,
        enabled = connections.count { it.isEnabled },
        ready = connections.count { it.connectionStatusLabel() == "Ready" },
        error = connections.count { it.authState == AuthState.ERROR },
    )

fun Connection.connectionStatusLabel(): String = when {
    authState == AuthState.ERROR -> "Error"
    !isEnabled -> "Disabled"
    authState == AuthState.EXPIRED -> "Warning"
    isHealthy || authState == AuthState.AUTHENTICATED -> "Ready"
    else -> "Enabled"
}

fun providerFilterLabels(presets: List<ProviderPreset>): List<String> =
    listOf("All") + ProviderCategory.values()
        .filter { category -> category != ProviderCategory.CUSTOM && presets.any { it.category == category } }
        .map { it.displayName }

fun homePromptSuggestions(): List<HomePromptSuggestion> = listOf(
    HomePromptSuggestion(
        title = "Continue the active task",
        prompt = "Continue the active task and show me the next reviewable step.",
        icon = HomePromptIcon.Continue,
    ),
    HomePromptSuggestion(
        title = "Review pending changes",
        prompt = "Review the pending changes and summarize what is safe to accept.",
        icon = HomePromptIcon.Review,
    ),
    HomePromptSuggestion(
        title = "Explain this project",
        prompt = "Read the project context and explain the architecture, risks, and next useful task.",
        icon = HomePromptIcon.Project,
    ),
    HomePromptSuggestion(
        title = "Create a safe checkpoint",
        prompt = "Create a checkpoint before the next meaningful edit and explain what it protects.",
        icon = HomePromptIcon.Checkpoint,
    ),
)

fun modelStatusLabel(modelName: String): String =
    if (modelName.isBlank() || modelName.equals("No model", ignoreCase = true)) {
        "Select model"
    } else {
        modelName
    }

fun trustModeStatusLabel(label: String): String = when {
    label.contains("guided", ignoreCase = true) || label.contains("assisted", ignoreCase = true) -> "Guided"
    label.contains("fully", ignoreCase = true) || label.contains("trust", ignoreCase = true) -> "Trusted"
    else -> "Manual"
}

fun workspaceContextSummary(
    projectName: String?,
    modelName: String,
    trustMode: String,
    connectionStatus: String,
): WorkspaceContextSummary {
    val title = projectName?.takeIf { it.isNotBlank() } ?: "No project attached"
    return WorkspaceContextSummary(
        projectTitle = title,
        metaLine = listOf(
            modelStatusLabel(modelName),
            trustModeStatusLabel(trustMode),
            connectionHealthLabel(connectionStatus),
        ).joinToString(" - "),
        hasProject = projectName?.isNotBlank() == true,
    )
}

fun homeActiveProject(
    activeTask: Task?,
    recentProjects: List<Project>,
    activeTaskProject: Project? = null,
): Project? =
    if (activeTask == null) {
        recentProjects.firstOrNull()
    } else {
        activeTaskProject
            ?.takeIf { it.id == activeTask.projectId }
            ?: recentProjects.firstOrNull { it.id == activeTask.projectId }
    }

fun homeActionProjectId(
    activeTask: Task?,
    activeProject: Project?,
): String? = activeTask?.projectId ?: activeProject?.id

fun homeContextTrustMode(
    activeTask: Task?,
    activeTaskProject: Project?,
    recentProjects: List<Project>,
): String =
    if (activeTask == null) {
        recentProjects.firstOrNull()?.trustMode?.displayName
    } else {
        activeTaskProject
            ?.takeIf { it.id == activeTask.projectId }
            ?.trustMode
            ?.displayName
    } ?: TrustMode.MANUAL.displayName

fun homeContextModelName(
    activeTask: Task?,
    projectPreferredModelName: String?,
    defaultModelName: String,
): String =
    activeTask
        ?.modelId
        ?.takeIf { it.isNotBlank() }
        ?: projectPreferredModelName?.takeIf { it.isNotBlank() }
        ?: defaultModelName

fun homeEmptyConversationSpec(hasProjectTarget: Boolean): HomeEmptyConversationSpec =
    if (hasProjectTarget) {
        HomeEmptyConversationSpec(
            primaryAction = "Start Project Task",
            body = "Project context is attached.",
            suggestion = "Read this project and suggest the next useful task.",
        )
    } else {
        HomeEmptyConversationSpec(
            primaryAction = "Attach Project",
            body = "or continue without one.",
            suggestion = "Start with a project-aware task",
        )
    }

fun homeWorkspaceContextSummary(
    activeTask: Task?,
    recentProjects: List<Project>,
    activeTaskProject: Project? = null,
    defaultModelName: String,
    defaultTrustMode: String,
    connectionStatus: String,
): WorkspaceContextSummary {
    val activeProject = homeActiveProject(activeTask, recentProjects, activeTaskProject)
    val activeModelName = activeTask?.modelId?.takeIf { it.isNotBlank() } ?: defaultModelName
    val activeTrustMode = activeProject?.trustMode?.displayName ?: defaultTrustMode

    return workspaceContextSummary(
        projectName = activeProject?.name,
        modelName = activeModelName,
        trustMode = activeTrustMode,
        connectionStatus = connectionStatus,
    )
}

fun homeConversationSendTarget(
    projectId: String?,
    prompt: String,
): HomeConversationSendTarget {
    val trimmedPrompt = prompt.trim()
    if (trimmedPrompt.isEmpty()) return HomeConversationSendTarget.None

    return projectId
        ?.takeIf { it.isNotBlank() }
        ?.let { HomeConversationSendTarget.StartProjectTask(it, trimmedPrompt) }
        ?: HomeConversationSendTarget.ContinueWithoutProject(trimmedPrompt)
}

fun homePendingPromptAction(
    projectId: String?,
    prompt: String?,
): HomePendingPromptAction {
    val trimmedPrompt = prompt?.trim().orEmpty()
    if (trimmedPrompt.isEmpty()) return HomePendingPromptAction.None

    return projectId
        ?.takeIf { it.isNotBlank() }
        ?.let { HomePendingPromptAction.RunInProject(it, trimmedPrompt) }
        ?: HomePendingPromptAction.AttachProject(trimmedPrompt)
}

fun composerModelLabel(modelName: String): String = modelStatusLabel(modelName)

fun workspaceDrawerDestinations(): List<NavigationDestinationSpec> = listOf(
    NavigationDestinationSpec(route = "Projects", title = "Projects", iconKey = "projects"),
    NavigationDestinationSpec(route = "model_catalog", title = "Models", iconKey = "models"),
    NavigationDestinationSpec(route = "Mcp", title = "UI Studio", iconKey = "mcp"),
    NavigationDestinationSpec(route = "Connections", title = "Connections", iconKey = "connections"),
    NavigationDestinationSpec(route = "usage", title = "Usage", iconKey = "usage"),
    NavigationDestinationSpec(route = "History", title = "History", iconKey = "history"),
    NavigationDestinationSpec(route = "Settings", title = "Settings", iconKey = "settings"),
)

fun modelCatalogHeaderTitle(): String = "Models"

fun modelCatalogSubtitle(selectionProjectId: String?): String =
    if (selectionProjectId != null) {
        "Used by the composer for this workspace"
    } else {
        "Select a model before project work"
    }

fun projectsHeaderSubtitle(projectCount: Int): String =
    if (projectCount == 0) {
        "Attach a workspace context"
    } else {
        "$projectCount ${if (projectCount == 1) "workspace context" else "workspace contexts"}"
    }

fun projectOverflowActions(hasProject: Boolean): List<ProjectActionSpec> =
    if (!hasProject) {
        emptyList()
    } else {
        listOf(
            ProjectActionSpec("workspace_settings", "Workspace Settings"),
        )
    }

fun contextualCardsForTask(task: Task?): List<ContextualAgentCardSpec> {
    if (task == null) return emptyList()

    val cards = mutableListOf<ContextualAgentCardSpec>()

    if (task.status == TaskStatus.FAILED) {
        cards += ContextualAgentCardSpec(
            kind = ContextualCardKind.Failure,
            title = task.warnings.firstOrNull() ?: "Build failed",
            body = task.warnings.joinToString("\n").ifBlank {
                task.outputSummary.ifBlank { "The agent could not complete this run." }
            },
            primaryAction = "Continue Analysis",
            secondaryAction = "Open on Desktop",
            collapsedWhenComplete = false,
        )
        return cards
    }

    if (task.status == TaskStatus.COMPLETED) {
        cards += ContextualAgentCardSpec(
            kind = ContextualCardKind.Result,
            title = "Task complete",
            body = task.outputSummary.ifBlank { "The requested work completed successfully." },
            primaryAction = "Open Result",
            collapsedWhenComplete = false,
        )
    }

    if (task.agentPlan.isNotEmpty() && task.status in setOf(
            TaskStatus.PLANNING,
            TaskStatus.READING_CONTEXT,
            TaskStatus.EDITING,
            TaskStatus.RUNNING_LOOP,
        )
    ) {
        cards += ContextualAgentCardSpec(
            kind = ContextualCardKind.Planning,
            title = "Planning",
            body = task.agentPlan.take(5).joinToString("\n") { it.description },
        )
    }

    if (task.status != TaskStatus.PLANNING && isLiveAgentStatus(task.status)) {
        cards += ContextualAgentCardSpec(
            kind = ContextualCardKind.Execution,
            title = task.status.displayName,
            body = task.toolCalls.lastOrNull()?.toolName
                ?: task.agentPlan.getOrNull(task.currentStepIndex)?.description
                ?: "Agent is working in this project.",
            primaryAction = "Open Task",
        )
    }

    if (task.status == TaskStatus.WAITING_APPROVAL) {
        cards += ContextualAgentCardSpec(
            kind = ContextualCardKind.Approval,
            title = "Approval required",
            body = task.approvals.lastOrNull()?.reason ?: "The agent needs confirmation before continuing.",
            primaryAction = "Allow",
            secondaryAction = "Deny",
            collapsedWhenComplete = false,
        )
    }

    if (task.filesChanged.isNotEmpty()) {
        cards += ContextualAgentCardSpec(
            kind = ContextualCardKind.Diff,
            title = "${task.filesChanged.size} ${if (task.filesChanged.size == 1) "file" else "files"} changed",
            body = task.filesChanged.take(6).joinToString("\n"),
            primaryAction = "Review Diff",
            collapsedWhenComplete = false,
        )
    }

    if (task.checkpoints.isNotEmpty()) {
        cards += ContextualAgentCardSpec(
            kind = ContextualCardKind.Checkpoint,
            title = "Checkpoint Created",
            body = task.checkpoints.last(),
            primaryAction = "Restore",
            collapsedWhenComplete = false,
        )
    }

    val totalTokens = task.usage.inputTokens + task.usage.outputTokens
    if (totalTokens > 0 || task.usage.toolCalls > 0) {
        cards += ContextualAgentCardSpec(
            kind = ContextualCardKind.Usage,
            title = "Usage",
            body = "Input ${formatCompactCount(task.usage.inputTokens)}\nOutput ${formatCompactCount(task.usage.outputTokens)}\nTool Calls ${formatCompactCount(task.usage.toolCalls)}",
        )
    }

    return cards
}

fun isLiveAgentStatus(status: TaskStatus): Boolean = status in setOf(
    TaskStatus.PLANNING,
    TaskStatus.READING_CONTEXT,
    TaskStatus.EDITING,
    TaskStatus.RUNNING_LOOP,
    TaskStatus.VERIFYING,
    TaskStatus.WAITING_APPROVAL,
    TaskStatus.EXECUTING,
    TaskStatus.RETRYING,
)

fun workspaceMenuSectionTitles(): List<String> = listOf(
    "PROJECT",
    "AGENT & MODEL",
    "USAGE & ACTIVITY",
    "CONNECTIONS",
    "OTHER",
)

fun projectMenuPrimaryActionTitle(hasProject: Boolean): String =
    if (hasProject) "Change Project" else "Add Project"

fun workspaceOtherMenuActionTitles(): List<String> = listOf(
    "Settings",
    "Help & Feedback",
)

fun settingsSectionTitles(): List<String> = listOf("PREFERENCES", "SYSTEM")

fun settingsPreferenceActionTitles(): List<String> = listOf(
)

fun settingsSystemActionTitles(): List<String> = listOf(
    "Connections",
)

fun historyFilterLabels(): List<String> = listOf("All", "Tasks", "Changes", "Events")

fun checkpointTotals(checkpoints: List<Checkpoint>): CheckpointTotals {
    val manual = checkpoints.count { it.isTrusted }
    return CheckpointTotals(
        total = checkpoints.size,
        automatic = checkpoints.size - manual,
        manual = manual,
    )
}

fun checkpointChatCardSpec(checkpoint: Checkpoint): CheckpointChatCardSpec =
    CheckpointChatCardSpec(
        title = "Restore point",
        body = checkpoint.reason.ifBlank { "Before agent changes" },
        meta = "${checkpoint.files.size} ${if (checkpoint.files.size == 1) "file" else "files"}",
        actionLabel = "Restore",
    )

fun formatCheckpointSizeBytes(bytes: Int): String = when {
    bytes <= 0 -> "0 KB"
    bytes >= 1_000_000 -> "${(bytes / 100_000.0).roundToInt() / 10.0}".trimTrailingZero() + " MB"
    else -> "${(bytes / 1_000.0).roundToInt().coerceAtLeast(1)} KB"
}

private fun Double.trimOneDecimal(): String =
    if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this)
    }

private fun String.trimTrailingZero(): String =
    if (endsWith(".0")) dropLast(2) else this
