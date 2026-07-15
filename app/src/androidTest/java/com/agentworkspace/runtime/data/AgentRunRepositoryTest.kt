package com.agentworkspace.runtime.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agentworkspace.data.db.AgentWorkspaceDatabase
import com.agentworkspace.data.db.entity.ProjectEntity
import com.agentworkspace.data.db.entity.TaskEntity
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.runtime.domain.IdGenerator
import com.agentworkspace.runtime.domain.ApprovalStatus
import com.agentworkspace.runtime.domain.RunCommandKind
import com.agentworkspace.runtime.domain.RunConfiguration
import com.agentworkspace.runtime.domain.RunEventKind
import com.agentworkspace.runtime.domain.RunStateMachine
import com.agentworkspace.runtime.domain.RunRecoveryPolicy
import com.agentworkspace.runtime.domain.RunStatus
import com.agentworkspace.readiness.domain.WorkspaceCapabilityProfiles
import com.agentworkspace.readiness.domain.WorkspaceKind
import com.agentworkspace.runtime.domain.RuntimeClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentRunRepositoryTest {
    private lateinit var database: AgentWorkspaceDatabase
    private lateinit var repository: RoomAgentRunRepository
    private val clock = RuntimeClock { 1_000L }
    private val ids = object : IdGenerator {
        private var value = 0

        override fun newId(): String = "id-${++value}"
    }

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AgentWorkspaceDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.projectDao().upsertProject(
            ProjectEntity.fromDomain(
                Project(id = "project", name = "Demo", path = "content://demo"),
            ),
        )
        database.taskDao().upsertTask(
            TaskEntity.fromDomain(
                Task(id = "task", projectId = "project", title = "Task", goal = "Goal"),
            ),
        )
        repository = RoomAgentRunRepository(
            database = database,
            dao = database.agentRunDao(),
            stateMachine = RunStateMachine(),
            recoveryPolicy = RunRecoveryPolicy(),
            clock = clock,
            ids = ids,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createRunAtomicallyIncludesStartCommandAndEvent() = runTest {
        val run = repository.createRun(startRequest())

        assertEquals(RunStatus.QUEUED, run.status)
        assertEquals(
            listOf(RunCommandKind.START),
            database.agentRunDao().getCommands(run.id).map { it.kind },
        )
        assertEquals(
            listOf(RunEventKind.RUN_CREATED, RunEventKind.MESSAGE),
            database.agentRunDao().getEventsOnce(run.id).map { it.kind },
        )
    }

    @Test
    fun invalidTransitionWritesNoEvent() = runTest {
        val run = repository.createRun(startRequest())
        listOf(
            RunStatus.PLANNING,
            RunStatus.READING_CONTEXT,
            RunStatus.CALLING_MODEL,
            RunStatus.COMPLETED,
        ).forEach { repository.transition(run.id, it) }
        val eventCount = database.agentRunDao().getEventsOnce(run.id).size

        var failure: Throwable? = null
        try {
            repository.transition(run.id, RunStatus.CALLING_MODEL)
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(eventCount, database.agentRunDao().getEventsOnce(run.id).size)
    }

    @Test
    fun processRecoveryPausesRunWithUncertainToolOutcome() = runTest {
        val run = repository.createRun(startRequest())
        listOf(
            RunStatus.PLANNING,
            RunStatus.READING_CONTEXT,
            RunStatus.CALLING_MODEL,
            RunStatus.EXECUTING_TOOL,
        ).forEach { repository.transition(run.id, it) }
        repository.appendEvent(
            run.id,
            RunEventKind.TOOL_STARTED,
            "{\"callId\":\"call-1\",\"tool\":\"write_file\"}",
        )

        val recoverable = repository.prepareProcessRecovery()
        val paused = repository.getRun(run.id)

        assertTrue(recoverable.isEmpty())
        assertEquals(RunStatus.PAUSED, paused?.status)
        assertEquals("UNCERTAIN_SIDE_EFFECT", paused?.lastErrorCode)
    }

    @Test
    fun cancellationExpiresPendingApproval() = runTest {
        val run = repository.createRun(startRequest())
        listOf(
            RunStatus.PLANNING,
            RunStatus.READING_CONTEXT,
            RunStatus.CALLING_MODEL,
        ).forEach { repository.transition(run.id, it) }
        val approval = repository.requestApproval(
            runId = run.id,
            actionType = "write_file",
            label = "write app.kt",
            reason = "approval required",
            risk = "standard",
        )

        repository.transition(run.id, RunStatus.CANCELLED)

        assertEquals(
            ApprovalStatus.EXPIRED,
            database.agentRunDao().getApproval(approval.id)?.status,
        )
    }

    private fun startRequest() = StartRunRequest(
        taskId = "task",
        projectId = "project",
        initialUserMessage = "Goal",
        configuration = RunConfiguration(
            connectionId = "connection",
            providerModelId = "gemini-2.5-pro",
            workspaceId = "content://demo",
            trustMode = TrustMode.MANUAL,
            transport = "GEMINI_NATIVE",
            workspaceKind = WorkspaceKind.LOCAL_SAF,
            capabilities = WorkspaceCapabilityProfiles.forKind(WorkspaceKind.LOCAL_SAF).capabilities,
            limitations = listOf("COMMAND_EXECUTION_UNAVAILABLE"),
        ),
    )
}
