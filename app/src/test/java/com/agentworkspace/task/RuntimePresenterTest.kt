package com.agentworkspace.task

import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.db.entity.runtime.ApprovalRequestEntity
import com.agentworkspace.data.db.entity.runtime.RunEventEntity
import com.agentworkspace.data.model.Task
import com.agentworkspace.data.model.TrustMode
import com.agentworkspace.readiness.domain.WorkspaceCapabilityProfiles
import com.agentworkspace.readiness.domain.WorkspaceKind
import com.agentworkspace.runtime.data.RunConfigurationCodec
import com.agentworkspace.runtime.domain.RunConfiguration
import com.agentworkspace.runtime.domain.ApprovalStatus
import com.agentworkspace.runtime.domain.RunEventKind
import com.agentworkspace.runtime.domain.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePresenterTest {
    @Test
    fun `timeline is projected from persisted task and run events`() {
        val task = Task(id = "task-1", projectId = "project-1", title = "Task", goal = "newer task value")
        val run = run(status = RunStatus.CALLING_MODEL)
        val events = listOf(
            event(1, RunEventKind.RUN_CREATED),
            event(2, RunEventKind.MESSAGE, "{\"role\":\"user\",\"content\":\"Fix the build\"}"),
            event(3, RunEventKind.MESSAGE, "{\"role\":\"assistant\",\"content\":\"I found it.\"}"),
            event(4, RunEventKind.TOOL_STARTED, "{\"tool\":\"read_file\",\"args\":\"{README.md}\"}"),
            event(5, RunEventKind.TOOL_FINISHED, "{\"tool\":\"read_file\",\"result\":\"ok\"}"),
            event(6, RunEventKind.APPROVAL_RESOLVED, "{\"approved\":false}"),
        )

        assertEquals(
            listOf(
                ChatLine(ChatLine.Role.USER, "Fix the build"),
                ChatLine(ChatLine.Role.ASSISTANT, "I found it."),
                ChatLine(ChatLine.Role.TOOL, "» read_file({README.md})"),
                ChatLine(ChatLine.Role.TOOL_RESULT, "✓ read_file: ok"),
                ChatLine(ChatLine.Role.SYSTEM, "Denied"),
            ),
            RuntimePresenter.timeline(task, run, events),
        )
    }

    @Test
    fun `running and approval states come from durable entities`() {
        assertTrue(RuntimePresenter.isRunning(run(RunStatus.WAITING_APPROVAL)))
        assertFalse(RuntimePresenter.isRunning(run(RunStatus.PAUSED)))
        assertFalse(RuntimePresenter.isRunning(run(RunStatus.COMPLETED)))

        val pending = RuntimePresenter.pendingApproval(
            ApprovalRequestEntity(
                id = "approval-1",
                runId = "run-1",
                actionType = "delete_file",
                label = "delete secrets.txt",
                reason = "Destructive action",
                risk = "destructive",
                status = ApprovalStatus.PENDING,
                requestedAt = 5,
            ),
        )

        assertEquals("approval-1", pending?.id)
        assertEquals("delete secrets.txt", pending?.label)
        assertEquals("Destructive action", pending?.reason)
        assertTrue(pending?.destructive == true)
    }

    @Test
    fun `local completion is explicitly not command verified`() {
        val run = run(
            status = RunStatus.COMPLETED,
            configurationJson = configurationJson(WorkspaceKind.LOCAL_SAF),
        )

        val timeline = RuntimePresenter.timeline(
            task = Task(id = "task-1", projectId = "project-1", title = "Task", goal = "Goal"),
            run = run,
            events = listOf(event(1, RunEventKind.COMPLETED)),
        )

        assertEquals(ChatLine(ChatLine.Role.SYSTEM, "Task complete"), timeline[0])
        assertEquals(
            ChatLine(ChatLine.Role.SYSTEM, "Verification: Not command-verified"),
            timeline[1],
        )
        assertEquals(
            listOf("COMMAND_EXECUTION_UNAVAILABLE"),
            RuntimePresenter.completionEvidence(run).limitations,
        )
    }

    @Test
    fun `github completion requires a persisted remote verification success event`() {
        val run = run(
            status = RunStatus.COMPLETED,
            configurationJson = configurationJson(WorkspaceKind.GITHUB),
        )

        assertEquals(
            "Verification unavailable",
            RuntimePresenter.completionEvidence(run, emptyList()).verificationLabel,
        )
        assertEquals(
            "Verified remotely",
            RuntimePresenter.completionEvidence(
                run,
                listOf(
                    event(
                        sequence = 1,
                        kind = RunEventKind.VERIFICATION_SUCCEEDED,
                        payload = "{\"method\":\"github_actions_apk\"}",
                    ),
                ),
            ).verificationLabel,
        )
    }

    @Test
    fun `invalid persisted configuration never implies verification`() {
        val evidence = RuntimePresenter.completionEvidence(
            run(status = RunStatus.COMPLETED, configurationJson = "{}"),
        )

        assertEquals("Verification unavailable", evidence.verificationLabel)
    }

    private fun run(
        status: RunStatus,
        configurationJson: String = "{}",
    ) = AgentRunEntity(
        id = "run-1",
        taskId = "task-1",
        projectId = "project-1",
        status = status,
        connectionId = "connection-1",
        providerModelId = "model-1",
        workspaceId = "content://workspace",
        configurationJson = configurationJson,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun event(sequence: Long, kind: RunEventKind, payload: String = "{}") = RunEventEntity(
        id = "event-$sequence",
        runId = "run-1",
        sequence = sequence,
        kind = kind,
        payloadJson = payload,
        createdAt = sequence,
    )

    private fun configurationJson(kind: WorkspaceKind): String {
        val profile = WorkspaceCapabilityProfiles.forKind(kind)
        return RunConfigurationCodec.encode(
            RunConfiguration(
                connectionId = "connection-1",
                providerModelId = "model-1",
                workspaceId = if (kind == WorkspaceKind.GITHUB) {
                    "github://owner/repo?branch=main"
                } else {
                    "content://workspace"
                },
                trustMode = TrustMode.GUIDED,
                transport = "OPENAI_COMPATIBLE",
                workspaceKind = kind,
                capabilities = profile.capabilities,
                limitations = if (kind == WorkspaceKind.LOCAL_SAF) {
                    listOf("COMMAND_EXECUTION_UNAVAILABLE")
                } else {
                    emptyList()
                },
            ),
        )
    }
}
