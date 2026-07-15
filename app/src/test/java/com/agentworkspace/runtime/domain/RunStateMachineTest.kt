package com.agentworkspace.runtime.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunStateMachineTest {
    private val machine = RunStateMachine()

    @Test
    fun `queued run may start planning`() {
        assertTrue(machine.canTransition(RunStatus.QUEUED, RunStatus.PLANNING))
    }

    @Test
    fun `waiting approval may resume tool execution`() {
        assertTrue(machine.canTransition(RunStatus.WAITING_APPROVAL, RunStatus.EXECUTING_TOOL))
    }

    @Test
    fun `active run may enter recovery`() {
        assertTrue(machine.canTransition(RunStatus.CALLING_MODEL, RunStatus.RECOVERING))
        assertTrue(machine.canTransition(RunStatus.WAITING_APPROVAL, RunStatus.RECOVERING))
    }

    @Test
    fun `terminal run cannot restart implicitly`() {
        RunStatus.entries.filter { it != RunStatus.COMPLETED }.forEach { target ->
            assertFalse(machine.canTransition(RunStatus.COMPLETED, target))
        }
    }

    @Test
    fun `pause cancel and failure remain distinct`() {
        assertTrue(machine.canTransition(RunStatus.CALLING_MODEL, RunStatus.PAUSED))
        assertTrue(machine.canTransition(RunStatus.CALLING_MODEL, RunStatus.CANCELLED))
        assertTrue(machine.canTransition(RunStatus.CALLING_MODEL, RunStatus.FAILED))
        assertEquals(3, setOf(RunStatus.PAUSED, RunStatus.CANCELLED, RunStatus.FAILED).size)
    }

    @Test(expected = IllegalStateException::class)
    fun `invalid transition throws before io`() {
        machine.requireTransition(RunStatus.COMPLETED, RunStatus.CALLING_MODEL)
    }
}
