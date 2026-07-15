package com.agentworkspace.runtime.compat

import com.agentworkspace.agent.runtime.AgentEvent
import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.ProviderType
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.runtime.data.RunProgressStore
import com.agentworkspace.runtime.data.RunTimelineStore
import com.agentworkspace.runtime.application.ApprovalRequester
import com.agentworkspace.runtime.domain.RunConfiguration
import com.agentworkspace.runtime.domain.RunEventKind
import com.agentworkspace.runtime.domain.RunStatus
import com.agentworkspace.trust.policy.AgentAction
import com.agentworkspace.trust.policy.ApprovalDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LegacyRunExecutorTest {
    @Test
    fun `executes local runtime with immutable run snapshot and persists its messages`() = runTest {
        val fixture = Fixture(workspaceId = "content://workspace/snapshot")

        fixture.executor.execute(fixture.run.id)

        assertEquals(LegacyRuntimeKind.LOCAL, fixture.gateway.kind)
        assertEquals("connection-1", fixture.gateway.request?.configuration?.connectionId)
        assertEquals("provider-model-exact", fixture.gateway.request?.configuration?.providerModelId)
        assertEquals("content://workspace/snapshot", fixture.gateway.request?.configuration?.workspaceId)
        assertSame(fixture.connection, fixture.gateway.request?.connection)
        assertEquals(listOf("assistant" to "durable answer"), fixture.timeline.messages)
        assertEquals(listOf(RunStatus.VERIFYING), fixture.progress.transitions)
    }

    @Test
    fun `uses github runtime and returns denied approval to model loop`() = runTest {
        val fixture = Fixture(workspaceId = "github://owner/repo?branch=main")
        fixture.gateway.requestApproval = true
        fixture.approvals.approved = false
        fixture.approvals.onRequest = { fixture.progress.status = RunStatus.WAITING_APPROVAL }

        fixture.executor.execute(fixture.run.id)

        assertEquals(LegacyRuntimeKind.GITHUB, fixture.gateway.kind)
        assertEquals(listOf(RunStatus.CALLING_MODEL, RunStatus.VERIFYING), fixture.progress.transitions)
    }

    private class Fixture(workspaceId: String) {
        val run = AgentRunEntity(
            id = "run-1",
            taskId = "task-1",
            projectId = "project-1",
            status = RunStatus.CALLING_MODEL,
            connectionId = "connection-1",
            providerModelId = "provider-model-exact",
            workspaceId = workspaceId,
            configurationJson = "{}",
            createdAt = 1,
            updatedAt = 1,
        )
        val task = Task(id = "task-1", projectId = "project-1", title = "Task", goal = "Goal")
        val project = Project(id = "project-1", name = "Project", path = "changed-after-start")
        val connection = Connection(
            id = "connection-1",
            name = "Connection",
            providerType = ProviderType.OPENAI,
        )
        val configuration = RunConfiguration(
            connectionId = "connection-1",
            providerModelId = "provider-model-exact",
            workspaceId = workspaceId,
            trustMode = TrustMode.GUIDED,
            transport = "OPENAI_COMPATIBLE",
        )
        val context = LegacyExecutionContext(run, task, project, connection, configuration)
        val progress = RecordingProgressStore(run)
        val timeline = RecordingTimelineStore()
        val gateway = RecordingGateway()
        val approvals = RecordingApprovalRequester()
        val executor = LegacyRunExecutor(
            contextLoader = LegacyRunContextLoader { context },
            progress = progress,
            gateway = gateway,
            approvals = approvals,
            eventWriter = LegacyEventWriter(timeline),
        )
    }

    private class RecordingProgressStore(initial: AgentRunEntity) : RunProgressStore {
        private var run = initial
        var status: RunStatus
            get() = run.status
            set(value) {
                run = run.copy(status = value)
            }
        val transitions = mutableListOf<RunStatus>()

        override suspend fun getRun(runId: String): AgentRunEntity = run

        override suspend fun transition(runId: String, status: RunStatus, payloadJson: String) {
            transitions += status
            run = run.copy(status = status)
        }
    }

    private class RecordingTimelineStore : RunTimelineStore {
        val messages = mutableListOf<Pair<String, String>>()

        override suspend fun appendEvent(runId: String, kind: RunEventKind, payloadJson: String) = Unit

        override suspend fun appendMessage(runId: String, role: String, content: String, toolCallId: String?) {
            messages += role to content
        }
    }

    private class RecordingGateway : LegacyRuntimeGateway {
        private val stream = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 4)
        var kind: LegacyRuntimeKind? = null
        var request: LegacyExecutionRequest? = null
        var requestApproval = false

        override fun events(kind: LegacyRuntimeKind): Flow<AgentEvent> = stream

        override suspend fun execute(
            kind: LegacyRuntimeKind,
            request: LegacyExecutionRequest,
            approve: suspend (AgentAction, ApprovalDecision) -> Boolean,
        ): Result<Task> {
            this.kind = kind
            this.request = request
            if (requestApproval) {
                approve(
                    AgentAction.WriteFile("app.kt"),
                    ApprovalDecision.required("write requires approval"),
                )
            }
            stream.emit(AgentEvent.Message(request.task.id, "durable answer"))
            stream.emit(AgentEvent.TaskComplete(request.task.id))
            return Result.success(request.task)
        }
    }

    private class RecordingApprovalRequester : ApprovalRequester {
        var approved = true
        var onRequest: () -> Unit = {}

        override suspend fun requestAndAwait(
            runId: String,
            action: AgentAction,
            decision: ApprovalDecision,
        ): Boolean {
            onRequest()
            return approved
        }
    }
}
