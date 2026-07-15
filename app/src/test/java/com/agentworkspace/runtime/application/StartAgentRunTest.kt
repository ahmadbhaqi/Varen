package com.agentworkspace.runtime.application

import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.ProviderType
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TaskStatus
import com.agentworkspace.readiness.application.RuntimeReadinessCoordinator
import com.agentworkspace.readiness.application.WorkspaceCapabilityResolver
import com.agentworkspace.readiness.domain.WorkspaceAccessChecker
import com.agentworkspace.readiness.domain.WorkspaceCapability
import com.agentworkspace.readiness.domain.WorkspaceKind
import com.agentworkspace.runtime.data.RunCreator
import com.agentworkspace.runtime.data.StartRunRequest
import com.agentworkspace.runtime.domain.RunStatus
import com.agentworkspace.runtime.service.RunLauncher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartAgentRunTest {
    @Test
    fun `blocked readiness returns corrective result without mutating task`() = runTest {
        val data = FakeRunStartDataSource(
            project = project(preferredConnectionId = null),
            connections = listOf(connection()),
        )
        val creator = RecordingRunCreator()
        val launcher = RecordingRunLauncher()

        val result = useCase(data, creator, launcher).execute(
            "project",
            "task",
            "fix build",
            "Fix build",
        )

        assertEquals(
            "CONNECTION_REQUIRED",
            (result as StartAgentRunResult.Blocked).blocker.code,
        )
        assertNull(data.savedTask)
        assertTrue(creator.requests.isEmpty())
        assertTrue(launcher.runIds.isEmpty())
    }

    @Test
    fun `run snapshot stores readiness capabilities and limitations`() = runTest {
        val data = FakeRunStartDataSource(
            project = project(),
            connections = listOf(connection()),
        )
        val creator = RecordingRunCreator()
        val launcher = RecordingRunLauncher()

        val result = useCase(data, creator, launcher).execute(
            "project",
            "task",
            "fix build",
            "Fix build",
        )

        val request = creator.requests.single()
        assertEquals("connection", request.configuration.connectionId)
        assertEquals("gemini-2.5-pro", request.configuration.providerModelId)
        assertEquals(WorkspaceKind.LOCAL_SAF, request.configuration.workspaceKind)
        assertTrue(WorkspaceCapability.READ_FILES in request.configuration.capabilities)
        assertTrue(WorkspaceCapability.WRITE_FILES in request.configuration.capabilities)
        assertTrue(WorkspaceCapability.RUN_COMMAND !in request.configuration.capabilities)
        assertEquals(
            listOf("COMMAND_EXECUTION_UNAVAILABLE"),
            request.configuration.limitations,
        )
        assertEquals("fix build", request.initialUserMessage)
        assertEquals("run-1", (result as StartAgentRunResult.Started).runId)
        assertEquals(listOf("run-1"), launcher.runIds)
        assertEquals(TaskStatus.QUEUED, data.savedTask?.status)
    }

    private fun useCase(
        data: RunStartDataSource,
        creator: RunCreator,
        launcher: RunLauncher,
    ) = StartAgentRun(
        dataSource = data,
        runCreator = creator,
        launcher = launcher,
        readinessCoordinator = RuntimeReadinessCoordinator(
            workspaceAccessChecker = WorkspaceAccessChecker { true },
            capabilityResolver = WorkspaceCapabilityResolver(),
        ),
    )

    private class FakeRunStartDataSource(
        private val project: Project,
        private val connections: List<Connection>,
    ) : RunStartDataSource {
        var savedTask: Task? = null

        override suspend fun getProject(projectId: String): Project? = project

        override suspend fun getConnections(): List<Connection> = connections

        override suspend fun getTask(taskId: String): Task = Task(
            id = taskId,
            projectId = project.id,
            title = "New task",
            goal = "",
        )

        override suspend fun saveTask(task: Task) {
            savedTask = task
        }
    }

    private class RecordingRunCreator : RunCreator {
        val requests = mutableListOf<StartRunRequest>()

        override suspend fun createRun(request: StartRunRequest): AgentRunEntity {
            requests += request
            return AgentRunEntity(
                id = "run-1",
                taskId = request.taskId,
                projectId = request.projectId,
                status = RunStatus.QUEUED,
                connectionId = request.configuration.connectionId,
                providerModelId = request.configuration.providerModelId,
                workspaceId = request.configuration.workspaceId,
                configurationJson = "{}",
                createdAt = 1L,
                updatedAt = 1L,
            )
        }
    }

    private class RecordingRunLauncher : RunLauncher {
        val runIds = mutableListOf<String>()

        override fun start(runId: String) {
            runIds += runId
        }

        override fun resume(runId: String) = Unit

        override fun pause(runId: String) = Unit

        override fun cancel(runId: String) = Unit
    }

    private fun project(preferredConnectionId: String? = "connection") = Project(
        id = "project",
        name = "Demo",
        path = "content://demo",
        preferredModelId = "model-app-id",
        preferredConnectionId = preferredConnectionId,
    )

    private fun model(connectionId: String = "connection") = ModelInfo(
        id = "model-app-id",
        name = "gemini-2.5-pro",
        connectionId = connectionId,
    )

    private fun connection() = Connection(
        id = "connection",
        name = "Google",
        providerType = ProviderType.GOOGLE,
        isEnabled = true,
        authState = AuthState.AUTHENTICATED,
        presetId = "google-gemini",
        models = listOf(model()),
    )
}
