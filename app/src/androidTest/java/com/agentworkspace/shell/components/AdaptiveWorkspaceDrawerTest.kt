package com.agentworkspace.shell.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agentworkspace.data.model.Project
import com.agentworkspace.shell.presentation.NavigationDestinationSpec
import com.agentworkspace.shell.theme.AgentWorkspaceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdaptiveWorkspaceDrawerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun globalDestinationsAndRecentProjectsRemainReachable() {
        var openedRoute: String? = null
        var openedProject: String? = null

        composeRule.setContent {
            AgentWorkspaceTheme {
                AdaptiveWorkspaceDrawer(
                    destinations = listOf(
                        NavigationDestinationSpec("model_catalog", "Models", "models"),
                        NavigationDestinationSpec("Settings", "Settings", "settings"),
                    ),
                    recentProjects = listOf(Project(id = "project", name = "IDE Android", path = "/workspace")),
                    activeProjectId = "project",
                    currentRoute = "Home",
                    connectionStatus = "Connected",
                    activeModelName = "Reasoning",
                    sessionTokens = 1200,
                    onHomeClick = { openedRoute = "Home" },
                    onProjectClick = { openedProject = it },
                    onDestinationClick = { openedRoute = it.route },
                )
            }
        }

        composeRule.onNodeWithText("IDE Android").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("project", openedProject) }
        composeRule.onNodeWithText("Models").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("model_catalog", openedRoute) }
    }
}
