package com.agentworkspace.task

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agentworkspace.data.model.Checkpoint
import com.agentworkspace.data.model.CheckpointFile
import com.agentworkspace.data.model.CheckpointScope
import com.agentworkspace.shell.theme.AgentWorkspaceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CheckpointRestoreCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restorePointStaysDistinctFromHistoryAndExposesItsAction() {
        val checkpoint = Checkpoint(
            id = "checkpoint-1",
            projectId = "project",
            taskId = "task",
            reason = "Before editing navigation",
            scope = CheckpointScope.MULTI_FILE,
            files = listOf(CheckpointFile("NavGraph.kt", "before", "hash")),
        )
        var restoredId: String? = null

        composeRule.setContent {
            AgentWorkspaceTheme {
                CheckpointRestoreCard(
                    checkpoint = checkpoint,
                    onRestore = { restoredId = checkpoint.id },
                )
            }
        }

        composeRule.onNodeWithText("Restore point").assertIsDisplayed()
        composeRule.onNodeWithText("Before editing navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Restore").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("checkpoint-1", restoredId) }
    }
}
