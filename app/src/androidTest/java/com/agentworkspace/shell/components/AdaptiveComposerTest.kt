package com.agentworkspace.shell.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import com.agentworkspace.shell.presentation.HomeModelOption
import com.agentworkspace.shell.theme.AgentWorkspaceTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class AdaptiveComposerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modelChoicesOpenInsideTheComposer() {
        var selectedModelId: String? = null
        composeRule.setContent {
            AgentWorkspaceTheme {
                AdaptiveComposer(
                    value = "",
                    onValueChange = {},
                    isRunning = false,
                    modelLabel = "Reasoning",
                    modelOptions = listOf(
                        HomeModelOption(
                            id = "reasoning",
                            connectionId = "connection",
                            name = "Reasoning",
                            detail = "Reasoning · tools",
                            enabled = true,
                            selected = true,
                        ),
                        HomeModelOption(
                            id = "fast",
                            connectionId = "connection",
                            name = "Fast",
                            detail = "Fast responses",
                            enabled = true,
                            selected = false,
                        ),
                        HomeModelOption(
                            id = "offline",
                            connectionId = "connection",
                            name = "Offline",
                            detail = "Unavailable",
                            enabled = false,
                            selected = false,
                        ),
                    ),
                    onModelSelected = { selectedModelId = it.id },
                    onAttach = {},
                    onSend = {},
                    onRunningAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Choose model").performClick()
        composeRule.onAllNodesWithText("Reasoning").assertCountEquals(2)
        composeRule.onNodeWithText("Reasoning · tools").assertIsDisplayed()
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
        composeRule.onNodeWithText("Unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Fast").performClick()
        composeRule.runOnIdle { assertEquals("fast", selectedModelId) }
        composeRule.onAllNodesWithText("Fast").assertCountEquals(0)
    }

    @Test
    fun sendBecomesStopWhileARequestIsRunning() {
        var sendCount = 0
        var stopCount = 0

        composeRule.setContent {
            AgentWorkspaceTheme {
                var running by remember { mutableStateOf(false) }
                AdaptiveComposer(
                    value = "Review this project",
                    onValueChange = {},
                    isRunning = running,
                    modelLabel = "Reasoning",
                    modelOptions = emptyList(),
                    onModelSelected = {},
                    onAttach = {},
                    onSend = {
                        sendCount += 1
                        running = true
                    },
                    onRunningAction = {
                        stopCount += 1
                        running = false
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Send prompt").performClick()
        composeRule.onNodeWithContentDescription("Stop task").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, sendCount)
            assertEquals(1, stopCount)
        }
    }

    @Test
    fun emptyModelMenuOffersARealConfigurationAction() {
        var configured = false
        composeRule.setContent {
            AgentWorkspaceTheme {
                AdaptiveComposer(
                    value = "",
                    onValueChange = {},
                    isRunning = false,
                    modelLabel = "Select model",
                    modelOptions = emptyList(),
                    onModelSelected = {},
                    onAttach = {},
                    onConfigureModels = { configured = true },
                    onSend = {},
                    onRunningAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Choose model").performClick()
        composeRule.onNodeWithText("No models available").assertIsDisplayed()
        composeRule.onNodeWithText("Configure connections").performClick()
        composeRule.runOnIdle { assertEquals(true, configured) }
    }

    @Test
    fun unavailableModelsStillOfferConnectionConfiguration() {
        var configured = false
        composeRule.setContent {
            AgentWorkspaceTheme {
                AdaptiveComposer(
                    value = "",
                    onValueChange = {},
                    isRunning = false,
                    modelLabel = "Select model",
                    modelOptions = listOf(
                        HomeModelOption(
                            id = "offline",
                            connectionId = "connection",
                            name = "Reasoning",
                            detail = "Authentication required",
                            enabled = false,
                            selected = false,
                        ),
                    ),
                    onModelSelected = {},
                    onAttach = {},
                    onConfigureModels = { configured = true },
                    onSend = {},
                    onRunningAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Choose model").performClick()
        composeRule.onNodeWithText("Authentication required").assertIsDisplayed()
        composeRule.onNodeWithText("Configure connections").performClick()
        composeRule.runOnIdle { assertEquals(true, configured) }
    }
}
