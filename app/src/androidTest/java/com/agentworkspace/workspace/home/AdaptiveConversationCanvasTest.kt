package com.agentworkspace.workspace.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import com.agentworkspace.shell.theme.AgentWorkspaceTheme
import org.junit.Rule
import org.junit.Test

class AdaptiveConversationCanvasTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyHeroLeavesTheLayoutAfterTheFirstPrompt() {
        lateinit var activateConversation: () -> Unit

        composeRule.setContent {
            AgentWorkspaceTheme {
                var active by remember { mutableStateOf(false) }
                activateConversation = { active = true }
                AdaptiveEmptyHero(
                    visible = !active,
                    hasProject = true,
                    onPrompt = {},
                    onAttachProject = {},
                )
            }
        }

        composeRule.onNodeWithText("What do you want to build?").assertIsDisplayed()
        composeRule.runOnUiThread { activateConversation() }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onAllNodesWithText("What do you want to build?").assertCountEquals(0)
    }
}
