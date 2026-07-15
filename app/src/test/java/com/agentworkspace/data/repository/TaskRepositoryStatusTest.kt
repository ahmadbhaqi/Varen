package com.agentworkspace.data.repository

import com.agentworkspace.data.model.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepositoryStatusTest {
    @Test
    fun activeTaskStatusesIncludeTheRuntimeLoopButExcludeTerminalStates() {
        assertTrue(TaskStatus.RUNNING_LOOP in ACTIVE_TASK_STATUSES)
        assertFalse(TaskStatus.COMPLETED in ACTIVE_TASK_STATUSES)
        assertFalse(TaskStatus.FAILED in ACTIVE_TASK_STATUSES)
    }
}
