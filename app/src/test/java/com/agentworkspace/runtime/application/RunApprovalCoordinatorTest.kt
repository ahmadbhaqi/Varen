package com.agentworkspace.runtime.application

import com.agentworkspace.data.db.entity.runtime.ApprovalRequestEntity
import com.agentworkspace.runtime.data.ApprovalStore
import com.agentworkspace.runtime.domain.ApprovalStatus
import com.agentworkspace.runtime.service.RunLauncher
import com.agentworkspace.trust.policy.AgentAction
import com.agentworkspace.trust.policy.ApprovalDecision
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunApprovalCoordinatorTest {
    @Test
    fun `pending approval can be resolved outside waiting coordinator`() = runTest {
        val store = FakeApprovalStore()
        val launcher = RecordingLauncher()
        val coordinator = RunApprovalCoordinator(store)
        val waiter = async {
            coordinator.requestAndAwait(
                runId = "run-1",
                action = AgentAction.WriteFile("src/App.kt"),
                decision = ApprovalDecision.required("File write requires approval"),
            )
        }
        advanceUntilIdle()

        val pending = store.current.value
        assertEquals(ApprovalStatus.PENDING, pending?.status)
        ResolveRunApproval(store, launcher).execute(checkNotNull(pending).id, approved = true)

        assertTrue(waiter.await())
        assertEquals(ApprovalStatus.APPROVED, store.current.value?.status)
        assertEquals(listOf("run-1"), launcher.resumedRuns)
    }

    @Test
    fun `denied approval returns false and resumes run for denial result`() = runTest {
        val store = FakeApprovalStore()
        val launcher = RecordingLauncher()
        val waiter = async {
            RunApprovalCoordinator(store).requestAndAwait(
                "run-1",
                AgentAction.DeleteFile("src/App.kt"),
                ApprovalDecision.required("Delete is destructive", isDestructive = true),
            )
        }
        advanceUntilIdle()

        ResolveRunApproval(store, launcher).execute(checkNotNull(store.current.value).id, approved = false)

        assertFalse(waiter.await())
        assertEquals(ApprovalStatus.DENIED, store.current.value?.status)
        assertEquals(listOf("run-1"), launcher.resumedRuns)
    }

    private class FakeApprovalStore : ApprovalStore {
        val current = MutableStateFlow<ApprovalRequestEntity?>(null)

        override suspend fun requestApproval(
            runId: String,
            actionType: String,
            label: String,
            reason: String,
            risk: String,
        ): ApprovalRequestEntity {
            val approval = ApprovalRequestEntity(
                id = "approval-1",
                runId = runId,
                actionType = actionType,
                label = label,
                reason = reason,
                risk = risk,
                status = ApprovalStatus.PENDING,
                requestedAt = 1L,
            )
            current.value = approval
            return approval
        }

        override fun observeApproval(approvalId: String): Flow<ApprovalRequestEntity?> = current

        override suspend fun resolveApproval(
            approvalId: String,
            approved: Boolean,
        ): ApprovalRequestEntity {
            val resolved = checkNotNull(current.value).copy(
                status = if (approved) ApprovalStatus.APPROVED else ApprovalStatus.DENIED,
                resolvedAt = 2L,
            )
            current.value = resolved
            return resolved
        }
    }

    private class RecordingLauncher : RunLauncher {
        val resumedRuns = mutableListOf<String>()

        override fun start(runId: String) = Unit

        override fun resume(runId: String) {
            resumedRuns += runId
        }

        override fun pause(runId: String) = Unit

        override fun cancel(runId: String) = Unit
    }
}
