package com.agentworkspace.shell.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.agentworkspace.shell.theme.AgentWorkspaceTheme
import org.junit.Rule
import org.junit.Test

class AdaptiveChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topBarAndContextStripExposeTheWorkspaceHierarchy() {
        composeRule.setContent {
            AgentWorkspaceTheme {
                AdaptiveScreen {
                    AdaptiveTopBar(
                        title = "Workspace",
                        subtitle = "Conversation",
                        leading = {
                            AdaptiveIconButton(Icons.Filled.Menu, "Open navigation", onClick = {})
                        },
                    )
                    AdaptiveContextStrip(
                        projectTitle = "IDE Android",
                        meta = "GPT-5 · Guided",
                        status = "Active",
                        onClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Open navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Conversation").assertIsDisplayed()
        composeRule.onNodeWithText("IDE Android").assertIsDisplayed()
        composeRule.onNodeWithText("GPT-5 · Guided").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()
    }
}
