package com.agentworkspace.shell.presentation

import com.agentworkspace.data.model.Checkpoint
import com.agentworkspace.data.model.CheckpointFile
import com.agentworkspace.data.model.CheckpointScope
import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.AgentStep
import com.agentworkspace.data.model.AvailabilityState
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ModelCapabilities
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.data.model.ProviderPresets
import com.agentworkspace.data.model.ProviderType
import com.agentworkspace.data.model.TrustMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFormattersTest {
    @Test
    fun compactCountUsesKAndMLabels() {
        assertEquals("0", formatCompactCount(0))
        assertEquals("999", formatCompactCount(999))
        assertEquals("1.2K", formatCompactCount(1_240))
        assertEquals("1.5M", formatCompactCount(1_520_000))
    }

    @Test
    fun progressPercentIsClamped() {
        assertEquals(0, formatProgressPercent(0, 0))
        assertEquals(40, formatProgressPercent(2, 5))
        assertEquals(100, formatProgressPercent(8, 5))
    }

    @Test
    fun conversationModeRemovesHeroAfterFirstPrompt() {
        assertEquals(WorkspaceConversationMode.Empty, homeConversationMode(null, null))
        assertEquals(WorkspaceConversationMode.Active, homeConversationMode("saved prompt", null))
        assertEquals(
            WorkspaceConversationMode.Active,
            homeConversationMode(null, task(status = TaskStatus.RUNNING_LOOP)),
        )
    }

    @Test
    fun terminalTaskRemainsVisibleUntilLiveWorkSupersedesIt() {
        val running = task(status = TaskStatus.RUNNING_LOOP)
        val completed = task(status = TaskStatus.COMPLETED, outputSummary = "Verified release build")

        assertEquals(listOf(running), homeConversationTasks(listOf(running), completed))
        assertEquals(listOf(completed), homeConversationTasks(emptyList(), completed))
        assertEquals(emptyList<Task>(), homeConversationTasks(emptyList(), null))
    }

    @Test
    fun modelOptionsExposeRealAvailabilityAndSelection() {
        val models = listOf(
            modelInfo(id = "reasoning", name = "Reasoning", available = true),
            modelInfo(id = "offline", name = "Offline", available = false),
        )

        val options = homeModelOptions(models, selectedModelId = "reasoning", hasProject = true)

        assertEquals(listOf("reasoning", "offline"), options.map { it.id })
        assertTrue(options.first().selected)
        assertTrue(options.first().enabled)
        assertFalse(options.last().enabled)
        assertEquals("Reasoning · tools", options.first().detail)
        assertEquals("Unavailable", options.last().detail)
    }

    @Test
    fun agentRunPresentationUsesHonestPhaseAndProgressCopy() {
        assertEquals(
            AgentRunPresentation(
                label = "Queued",
                headline = "Preparing task",
                detail = "Waiting for the agent to begin.",
                currentStep = 0,
                totalSteps = 0,
                isTerminal = false,
            ),
            agentRunPresentation(
                status = TaskStatus.QUEUED,
                plan = emptyList(),
                currentStepIndex = 0,
            ),
        )

        val plan = listOf(
            AgentStep(description = "Inspect the current implementation"),
            AgentStep(description = "Apply the focused change"),
        )
        assertEquals(
            AgentRunPresentation(
                label = "Editing",
                headline = "Apply the focused change",
                detail = "Step 2 of 2",
                currentStep = 2,
                totalSteps = 2,
                isTerminal = false,
            ),
            agentRunPresentation(
                status = TaskStatus.EDITING,
                plan = plan,
                currentStepIndex = 1,
            ),
        )
    }

    @Test
    fun agentRunPresentationMakesApprovalAndFailuresActionable() {
        assertEquals(
            AgentRunPresentation(
                label = "Approval needed",
                headline = "Review the proposed action",
                detail = "The agent is waiting for your decision.",
                currentStep = 0,
                totalSteps = 0,
                isTerminal = false,
            ),
            agentRunPresentation(
                status = TaskStatus.WAITING_APPROVAL,
                plan = emptyList(),
                currentStepIndex = 0,
            ),
        )
        assertEquals(
            AgentRunPresentation(
                label = "Needs attention",
                headline = "Task did not complete",
                detail = "Review the activity log for the failure details.",
                currentStep = 0,
                totalSteps = 0,
                isTerminal = true,
            ),
            agentRunPresentation(
                status = TaskStatus.FAILED,
                plan = emptyList(),
                currentStepIndex = 0,
            ),
        )
    }

    @Test
    fun connectionHealthNormalizesLabels() {
        assertEquals("Healthy", connectionHealthLabel("Connected"))
        assertEquals("Needs Auth", connectionHealthLabel("Auth needed"))
        assertEquals("Offline", connectionHealthLabel("No connection"))
    }

    @Test
    fun connectionTotalsCountEnabledReadyAndErrors() {
        val connections = listOf(
            connection(isEnabled = true, isHealthy = true, authState = AuthState.AUTHENTICATED),
            connection(isEnabled = true, isHealthy = false, authState = AuthState.AUTHENTICATED),
            connection(isEnabled = false, isHealthy = false, authState = AuthState.NOT_AUTHENTICATED),
            connection(isEnabled = true, isHealthy = false, authState = AuthState.ERROR),
        )

        assertEquals(
            ConnectionTotals(total = 4, enabled = 3, ready = 2, error = 1),
            connectionTotals(connections),
        )
    }

    @Test
    fun connectionStatusLabelPrioritizesActionableState() {
        assertEquals("Error", connection(authState = AuthState.ERROR).connectionStatusLabel())
        assertEquals("Disabled", connection(isEnabled = false, authState = AuthState.AUTHENTICATED).connectionStatusLabel())
        assertEquals("Warning", connection(authState = AuthState.EXPIRED).connectionStatusLabel())
        assertEquals("Ready", connection(isHealthy = true, authState = AuthState.AUTHENTICATED).connectionStatusLabel())
        assertEquals("Enabled", connection(isEnabled = true, authState = AuthState.NOT_AUTHENTICATED).connectionStatusLabel())
    }

    @Test
    fun providerFiltersUseProjectCatalogCategories() {
        assertEquals(
            listOf("All", "Official", "Aggregator", "Open Source", "Subscription", "Free Tier"),
            providerFilterLabels(ProviderPresets.all),
        )
    }

    @Test
    fun homePromptSuggestionsMatchReferenceOrder() {
        assertEquals(
            listOf(
                "Continue the active task",
                "Review pending changes",
                "Explain this project",
                "Create a safe checkpoint",
            ),
            homePromptSuggestions().map { it.title },
        )
    }

    @Test
    fun modelStatusUsesActionableFallback() {
        assertEquals("Select model", modelStatusLabel(""))
        assertEquals("Select model", modelStatusLabel("No model"))
        assertEquals("Agent Model", modelStatusLabel("Agent Model"))
    }

    @Test
    fun trustModeUsesCompactWorkspaceLabels() {
        assertEquals("Guided", trustModeStatusLabel("Guided / assisted approval"))
        assertEquals("Trusted", trustModeStatusLabel("Fully trust agent"))
        assertEquals("Manual", trustModeStatusLabel("Manual approval"))
    }

    @Test
    fun menuSectionTitlesMatchReferenceGrouping() {
        assertEquals(
            listOf(
                "PROJECT",
                "AGENT & MODEL",
                "USAGE & ACTIVITY",
                "CONNECTIONS",
                "OTHER",
            ),
            workspaceMenuSectionTitles(),
        )
    }

    @Test
    fun projectMenuPrimaryActionChangesWhenNoProjectExists() {
        assertEquals("Add Project", projectMenuPrimaryActionTitle(hasProject = false))
        assertEquals("Change Project", projectMenuPrimaryActionTitle(hasProject = true))
    }

    @Test
    fun otherMenuDoesNotIncludeLogout() {
        assertEquals(
            listOf("Settings", "Help & Feedback"),
            workspaceOtherMenuActionTitles(),
        )
    }

    @Test
    fun settingsMenuIsOpenSourceFriendly() {
        assertEquals(
            listOf("PREFERENCES", "SYSTEM"),
            settingsSectionTitles(),
        )
        assertEquals(
            emptyList<String>(),
            settingsPreferenceActionTitles(),
        )
        assertEquals(
            listOf("Connections"),
            settingsSystemActionTitles(),
        )
    }

    @Test
    fun historyFilterLabelsMatchReferenceTabs() {
        assertEquals(listOf("All", "Tasks", "Changes", "Events"), historyFilterLabels())
    }

    @Test
    fun checkpointTotalsSeparateAutomaticAndManual() {
        val checkpoints = listOf(
            checkpoint(CheckpointScope.MULTI_FILE, isTrusted = false),
            checkpoint(CheckpointScope.SINGLE_FILE, isTrusted = false),
            checkpoint(CheckpointScope.PROJECT_WIDE, isTrusted = true),
        )

        assertEquals(CheckpointTotals(total = 3, automatic = 2, manual = 1), checkpointTotals(checkpoints))
    }

    @Test
    fun checkpointFileSizeUsesCompactMegabytes() {
        assertEquals("0 KB", formatCheckpointSizeBytes(0))
        assertEquals("24 KB", formatCheckpointSizeBytes(24_100))
        assertEquals("2.3 MB", formatCheckpointSizeBytes(2_300_000))
    }

    @Test
    fun checkpointChatSpecUsesRestoreLanguage() {
        val spec = checkpointChatCardSpec(
            Checkpoint(
                projectId = "project",
                reason = "Before editing navigation",
                scope = CheckpointScope.MULTI_FILE,
                files = listOf(
                    CheckpointFile("NavGraph.kt", "before", "hash-1"),
                    CheckpointFile("Drawer.kt", "before", "hash-2"),
                ),
            ),
        )

        assertEquals("Restore point", spec.title)
        assertEquals("Before editing navigation", spec.body)
        assertEquals("2 files", spec.meta)
        assertEquals("Restore", spec.actionLabel)
    }

    @Test
    fun workspaceContextSummaryUsesOnlyProjectModelTrustAndConnection() {
        assertEquals(
            WorkspaceContextSummary(
                projectTitle = "Mobile Banking App",
                metaLine = "GPT-5.5 - Guided - Healthy",
                hasProject = true,
            ),
            workspaceContextSummary(
                projectName = "Mobile Banking App",
                modelName = "GPT-5.5",
                trustMode = "Guided / assisted approval",
                connectionStatus = "Connected",
            ),
        )

        assertEquals(
            WorkspaceContextSummary(
                projectTitle = "No project attached",
                metaLine = "Select model - Manual - Offline",
                hasProject = false,
            ),
            workspaceContextSummary(
                projectName = null,
                modelName = "No model",
                trustMode = "Manual approval",
                connectionStatus = "No connection",
            ),
        )
    }

    @Test
    fun homeWorkspaceContextFollowsActiveTaskProjectAndModel() {
        val firstRecentProject = project(
            id = "settings",
            name = "Settings Tool",
            trustMode = TrustMode.MANUAL,
        )
        val activeProject = project(
            id = "banking",
            name = "Mobile Banking App",
            trustMode = TrustMode.GUIDED,
        )
        val activeTask = task(
            projectId = "banking",
            status = TaskStatus.EDITING,
            modelId = "GPT-5.5",
        )

        assertEquals(
            WorkspaceContextSummary(
                projectTitle = "Mobile Banking App",
                metaLine = "GPT-5.5 - Guided - Healthy",
                hasProject = true,
            ),
            homeWorkspaceContextSummary(
                activeTask = activeTask,
                recentProjects = listOf(firstRecentProject, activeProject),
                defaultModelName = "Global Model",
                defaultTrustMode = "Manual Approval",
                connectionStatus = "Connected",
            ),
        )
    }

    @Test
    fun homeWorkspaceContextDoesNotFallbackToWrongProjectForActiveTask() {
        val unrelatedRecentProject = project(
            id = "settings",
            name = "Settings Tool",
            trustMode = TrustMode.MANUAL,
        )
        val activeTask = task(
            projectId = "banking",
            status = TaskStatus.EDITING,
            modelId = "GPT-5.5",
        )

        assertEquals(
            null,
            homeActiveProject(
                activeTask = activeTask,
                recentProjects = listOf(unrelatedRecentProject),
            ),
        )
        assertEquals(
            WorkspaceContextSummary(
                projectTitle = "No project attached",
                metaLine = "GPT-5.5 - Manual - Healthy",
                hasProject = false,
            ),
            homeWorkspaceContextSummary(
                activeTask = activeTask,
                recentProjects = listOf(unrelatedRecentProject),
                defaultModelName = "Global Model",
                defaultTrustMode = "Manual Approval",
                connectionStatus = "Connected",
            ),
        )
    }

    @Test
    fun homeWorkspaceContextUsesResolvedActiveTaskProjectOutsideRecents() {
        val unrelatedRecentProject = project(
            id = "settings",
            name = "Settings Tool",
            trustMode = TrustMode.MANUAL,
        )
        val resolvedProject = project(
            id = "banking",
            name = "Mobile Banking App",
            trustMode = TrustMode.GUIDED,
        )
        val activeTask = task(
            projectId = "banking",
            status = TaskStatus.EDITING,
            modelId = "GPT-5.5",
        )

        assertEquals(
            resolvedProject,
            homeActiveProject(
                activeTask = activeTask,
                recentProjects = listOf(unrelatedRecentProject),
                activeTaskProject = resolvedProject,
            ),
        )
        assertEquals(
            WorkspaceContextSummary(
                projectTitle = "Mobile Banking App",
                metaLine = "GPT-5.5 - Guided - Healthy",
                hasProject = true,
            ),
            homeWorkspaceContextSummary(
                activeTask = activeTask,
                recentProjects = listOf(unrelatedRecentProject),
                activeTaskProject = resolvedProject,
                defaultModelName = "Global Model",
                defaultTrustMode = "Manual Approval",
                connectionStatus = "Connected",
            ),
        )
    }

    @Test
    fun homeContextTrustModeDoesNotBorrowRecentProjectForUnresolvedActiveTask() {
        val unrelatedRecentProject = project(
            id = "settings",
            name = "Settings Tool",
            trustMode = TrustMode.FULLY_TRUST,
        )
        val resolvedProject = project(
            id = "banking",
            name = "Mobile Banking App",
            trustMode = TrustMode.GUIDED,
        )
        val activeTask = task(
            projectId = "banking",
            status = TaskStatus.EDITING,
        )

        assertEquals(
            TrustMode.MANUAL.displayName,
            homeContextTrustMode(
                activeTask = activeTask,
                activeTaskProject = null,
                recentProjects = listOf(unrelatedRecentProject),
            ),
        )
        assertEquals(
            TrustMode.GUIDED.displayName,
            homeContextTrustMode(
                activeTask = activeTask,
                activeTaskProject = resolvedProject,
                recentProjects = listOf(unrelatedRecentProject),
            ),
        )
        assertEquals(
            TrustMode.FULLY_TRUST.displayName,
            homeContextTrustMode(
                activeTask = null,
                activeTaskProject = null,
                recentProjects = listOf(unrelatedRecentProject),
            ),
        )
    }

    @Test
    fun homeContextModelNamePrefersTaskThenProjectThenDefault() {
        assertEquals(
            "Task Model",
            homeContextModelName(
                activeTask = task(
                    status = TaskStatus.EDITING,
                    modelId = "Task Model",
                ),
                projectPreferredModelName = "Project Model",
                defaultModelName = "Global Model",
            ),
        )
        assertEquals(
            "Project Model",
            homeContextModelName(
                activeTask = null,
                projectPreferredModelName = "Project Model",
                defaultModelName = "Global Model",
            ),
        )
        assertEquals(
            "Global Model",
            homeContextModelName(
                activeTask = null,
                projectPreferredModelName = null,
                defaultModelName = "Global Model",
            ),
        )
    }

    @Test
    fun homeEmptyConversationCopyMatchesProjectPresence() {
        assertEquals(
            HomeEmptyConversationSpec(
                primaryAction = "Start Project Task",
                body = "Project context is attached.",
                suggestion = "Read this project and suggest the next useful task.",
            ),
            homeEmptyConversationSpec(hasProjectTarget = true),
        )
        assertEquals(
            HomeEmptyConversationSpec(
                primaryAction = "Attach Project",
                body = "or continue without one.",
                suggestion = "Start with a project-aware task",
            ),
            homeEmptyConversationSpec(hasProjectTarget = false),
        )
    }

    @Test
    fun homeActiveProjectUsesFirstRecentProjectOnlyWithoutActiveTask() {
        val recentProject = project(
            id = "settings",
            name = "Settings Tool",
            trustMode = TrustMode.MANUAL,
        )

        assertEquals(
            recentProject,
            homeActiveProject(
                activeTask = null,
                recentProjects = listOf(recentProject),
            ),
        )
    }

    @Test
    fun homeActionProjectIdPrefersActiveTaskProject() {
        val visibleProject = project(
            id = "settings",
            name = "Settings Tool",
            trustMode = TrustMode.MANUAL,
        )
        val activeTask = task(
            projectId = "banking",
            status = TaskStatus.EDITING,
        )

        assertEquals(
            "banking",
            homeActionProjectId(
                activeTask = activeTask,
                activeProject = null,
            ),
        )
        assertEquals(
            "banking",
            homeActionProjectId(
                activeTask = activeTask,
                activeProject = visibleProject,
            ),
        )
        assertEquals(
            "settings",
            homeActionProjectId(
                activeTask = null,
                activeProject = visibleProject,
            ),
        )
    }

    @Test
    fun homeConversationSendTargetKeepsNoProjectPromptLocal() {
        assertEquals(
            HomeConversationSendTarget.ContinueWithoutProject("Explain this idea"),
            homeConversationSendTarget(
                projectId = null,
                prompt = "  Explain this idea  ",
            ),
        )
        assertEquals(
            HomeConversationSendTarget.StartProjectTask(
                projectId = "project",
                prompt = "Run tests",
            ),
            homeConversationSendTarget(
                projectId = "project",
                prompt = "Run tests",
            ),
        )
        assertEquals(
            HomeConversationSendTarget.None,
            homeConversationSendTarget(projectId = "project", prompt = " "),
        )
    }

    @Test
    fun homePendingPromptActionRunsSavedPromptWhenProjectIsAvailable() {
        assertEquals(
            HomePendingPromptAction.AttachProject("Explain this idea"),
            homePendingPromptAction(
                projectId = null,
                prompt = "  Explain this idea  ",
            ),
        )
        assertEquals(
            HomePendingPromptAction.RunInProject(
                projectId = "project",
                prompt = "Explain this idea",
            ),
            homePendingPromptAction(
                projectId = "project",
                prompt = "  Explain this idea  ",
            ),
        )
        assertEquals(
            HomePendingPromptAction.None,
            homePendingPromptAction(projectId = "project", prompt = null),
        )
    }

    @Test
    fun composerModelLabelStaysCompactAndActionable() {
        assertEquals("Select model", composerModelLabel(""))
        assertEquals("Select model", composerModelLabel("No model"))
        assertEquals("GPT-5.5", composerModelLabel("GPT-5.5"))
    }

    @Test
    fun drawerDestinationsContainOnlyGlobalEntryPoints() {
        assertEquals(
            listOf("Projects", "Models", "UI Studio", "Connections", "Usage", "History", "Settings"),
            workspaceDrawerDestinations().map { it.title },
        )
        assertEquals(
            listOf("Projects", "model_catalog", "Mcp", "Connections", "usage", "History", "Settings"),
            workspaceDrawerDestinations().map { it.route },
        )
        assertFalse(workspaceDrawerDestinations().any { it.route == "Checkpoints" })
    }

    @Test
    fun secondaryEntryHeadersSupportConversationWorkspace() {
        assertEquals("Models", modelCatalogHeaderTitle())
        assertEquals("Used by the composer for this workspace", modelCatalogSubtitle(selectionProjectId = "project"))
        assertEquals("Select a model before project work", modelCatalogSubtitle(selectionProjectId = null))
        assertEquals("Attach a workspace context", projectsHeaderSubtitle(projectCount = 0))
        assertEquals("1 workspace context", projectsHeaderSubtitle(projectCount = 1))
        assertEquals("3 workspace contexts", projectsHeaderSubtitle(projectCount = 3))
    }

    @Test
    fun projectOverflowActionsAreProjectSpecificOnly() {
        assertEquals(emptyList<ProjectActionSpec>(), projectOverflowActions(hasProject = false))
        assertEquals(
            listOf(
                ProjectActionSpec("workspace_settings", "Workspace Settings"),
            ),
            projectOverflowActions(hasProject = true),
        )
    }

    @Test
    fun contextualCardsFollowTaskState() {
        val planning = task(
            status = TaskStatus.PLANNING,
            agentPlan = listOf(
                AgentStep(description = "Read auth module"),
                AgentStep(description = "Inspect routes"),
            ),
        )
        assertEquals(
            listOf(
                ContextualAgentCardSpec(
                    kind = ContextualCardKind.Planning,
                    title = "Planning",
                    body = "Read auth module\nInspect routes",
                    primaryAction = null,
                    secondaryAction = null,
                    collapsedWhenComplete = true,
                ),
            ),
            contextualCardsForTask(planning),
        )

        val changed = task(
            status = TaskStatus.VERIFYING,
            filesChanged = listOf("app/src/main/Login.kt", "app/src/main/Auth.kt"),
        )
        assertEquals(
            ContextualAgentCardSpec(
                kind = ContextualCardKind.Diff,
                title = "2 files changed",
                body = "app/src/main/Login.kt\napp/src/main/Auth.kt",
                primaryAction = "Review Diff",
                secondaryAction = null,
                collapsedWhenComplete = false,
            ),
            contextualCardsForTask(changed).first { it.kind == ContextualCardKind.Diff },
        )

        val failed = task(status = TaskStatus.FAILED, warnings = listOf("Build failed"))
        assertEquals(
            ContextualAgentCardSpec(
                kind = ContextualCardKind.Failure,
                title = "Build failed",
                body = "Build failed",
                primaryAction = "Continue Analysis",
                secondaryAction = "Open on Desktop",
                collapsedWhenComplete = false,
            ),
            contextualCardsForTask(failed).first(),
        )

        val completed = task(
            status = TaskStatus.COMPLETED,
            outputSummary = "Verified release build",
        )
        assertEquals(
            ContextualAgentCardSpec(
                kind = ContextualCardKind.Result,
                title = "Task complete",
                body = "Verified release build",
                primaryAction = "Open Result",
                secondaryAction = null,
                collapsedWhenComplete = false,
            ),
            contextualCardsForTask(completed).first(),
        )
    }

    private fun checkpoint(scope: CheckpointScope, isTrusted: Boolean): Checkpoint =
        Checkpoint(
            projectId = "project",
            scope = scope,
            files = listOf(CheckpointFile("file.kt", "content", "hash")),
            reason = "Reason",
            isTrusted = isTrusted,
        )

    private fun connection(
        isEnabled: Boolean = true,
        isHealthy: Boolean = false,
        authState: AuthState = AuthState.NOT_AUTHENTICATED,
    ): Connection =
        Connection(
            name = "OpenAI",
            providerType = ProviderType.OPENAI,
            baseUrl = "https://api.openai.com/v1/chat/completions",
            isEnabled = isEnabled,
            isHealthy = isHealthy,
            authState = authState,
            presetId = "openai",
        )

    private fun project(
        id: String,
        name: String,
        trustMode: TrustMode,
    ): Project =
        Project(
            id = id,
            name = name,
            path = "/workspace/$id",
            trustMode = trustMode,
        )

    private fun modelInfo(id: String, name: String, available: Boolean): ModelInfo =
        ModelInfo(
            id = id,
            name = name,
            connectionId = "connection",
            capabilities = ModelCapabilities(reasoning = id == "reasoning", toolUse = true),
            availabilityState = if (available) AvailabilityState.AVAILABLE else AvailabilityState.UNAVAILABLE,
        )

    private fun task(
        projectId: String = "project",
        status: TaskStatus,
        modelId: String? = null,
        agentPlan: List<AgentStep> = emptyList(),
        filesChanged: List<String> = emptyList(),
        warnings: List<String> = emptyList(),
        outputSummary: String = "",
    ): Task =
        Task(
            projectId = projectId,
            title = "Improve login",
            goal = "Improve the login flow",
            status = status,
            modelId = modelId,
            agentPlan = agentPlan,
            filesChanged = filesChanged,
            warnings = warnings,
            outputSummary = outputSummary,
        )
}
