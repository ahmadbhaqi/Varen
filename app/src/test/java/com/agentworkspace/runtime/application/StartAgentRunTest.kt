package com.agentworkspace.runtime.application

import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.ProviderType
import com.agentworkspace.data.model.Task
import com.agentworkspace.runtime.data.RunCreator
import com.agentworkspace.runtime.data.StartRunRequest
import com.agentworkspace.runtime.domain.RunStatus
import com.agentworkspace.runtime.service.RunLauncher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartAgentRunTest {
    @Test
    fun `missing preferred connection does not fall back`() = runTest {
        val data = FakeRunStartDataSource(
            project = project(preferredConnectionId = "missing"),
            model = model(connectionId = "model-connection"),
            connection = null,
        )
        val creator = RecordingRunCreator()
        val launcher = RecordingRunLauncher()
        val useCase = StartAgentRun(data, creator, launcher)

        var failure: Throwable? = null
        try {
            useCase.execute("project", "task", "fix build", "Fix build")
        } catch (error: Throwable) {
            failure = error
        }

        assertEquals("Selected connection is unavailable", failure?.message)
        assertTrue(creator.requests.isEmpty())
        assertTrue(launcher.runIds.isEmpty())
    }

    @Test
    fun `run snapshot stores provider model id separately`() = runTest {
        val data = FakeRunStartDataSource(
            project = project(preferredConnectionId = "connection"),
            model = model(connectionId = "connection"),
            connection = Connection(
                id = "connection",
                name = "Google",
                providerType = ProviderType.GOOGLE,
                isEnabled = true,
            ),
        )
        val creator = RecordingRunCreator()
        val launcher = RecordingRunLauncher()
        val useCase = StartAgentRun(data, creator, launcher)

        val runId = useCase.execute("project", "task", "fix build", "Fix build")

        val request = creator.requests.single()
        assertEquals("connection", request.configuration.connectionId)
        assertEquals("gemini-2.5-pro", request.configuration.providerModelId)
        assertEquals("fix build", request.initialUserMessage)
        assertEquals("run-1", runId)
        assertEquals(listOf("run-1"), launcher.runIds)
        assertEquals("gemini-2.5-pro", data.savedTask?.modelId)
        assertEquals("connection", data.savedTask?.connectionId)
    }

    private class FakeRunStartDataSource(
        private val project: Project,
        private val model: ModelInfo,
        private val connection: Connection?,
    ) : RunStartDataSource {
        var savedTask: Task? = null

        override suspend fun getProject(projectId: String): Project? = project

        override suspend fun getModel(modelId: String): ModelInfo? = model

        override suspend fun getConnection(connectionId: String): Connection? = connection

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

    private fun project(preferredConnectionId: String) = Project(
        id = "project",
        name = "Demo",
        path = "content://demo",
        preferredModelId = "model-app-id",
        preferredConnectionId = preferredConnectionId,
    )

    private fun model(connectionId: String) = ModelInfo(
        id = "model-app-id",
        name = "gemini-2.5-pro",
        connectionId = connectionId,
    )
}
