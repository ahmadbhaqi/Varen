package com.agentworkspace.runtime.domain

import javax.inject.Inject

class RunStateMachine @Inject constructor() {
    private val transitions: Map<RunStatus, Set<RunStatus>> = mapOf(
        RunStatus.QUEUED to setOf(
            RunStatus.PLANNING,
            RunStatus.PAUSED,
            RunStatus.CANCELLED,
            RunStatus.FAILED,
        ),
        RunStatus.PLANNING to activeNext(RunStatus.READING_CONTEXT),
        RunStatus.READING_CONTEXT to activeNext(RunStatus.CALLING_MODEL),
        RunStatus.CALLING_MODEL to activeNext(
            RunStatus.WAITING_APPROVAL,
            RunStatus.EXECUTING_TOOL,
            RunStatus.VERIFYING,
            RunStatus.COMPLETED,
        ),
        RunStatus.WAITING_APPROVAL to setOf(
            RunStatus.EXECUTING_TOOL,
            RunStatus.CALLING_MODEL,
            RunStatus.RECOVERING,
            RunStatus.PAUSED,
            RunStatus.CANCELLED,
            RunStatus.FAILED,
        ),
        RunStatus.EXECUTING_TOOL to activeNext(
            RunStatus.EDITING,
            RunStatus.CALLING_MODEL,
            RunStatus.VERIFYING,
        ),
        RunStatus.EDITING to activeNext(RunStatus.CALLING_MODEL, RunStatus.VERIFYING),
        RunStatus.VERIFYING to activeNext(RunStatus.CALLING_MODEL, RunStatus.COMPLETED),
        RunStatus.RETRYING to activeNext(RunStatus.CALLING_MODEL, RunStatus.EXECUTING_TOOL),
        RunStatus.RECOVERING to setOf(
            RunStatus.PLANNING,
            RunStatus.READING_CONTEXT,
            RunStatus.CALLING_MODEL,
            RunStatus.WAITING_APPROVAL,
            RunStatus.EXECUTING_TOOL,
            RunStatus.PAUSED,
            RunStatus.CANCELLED,
            RunStatus.FAILED,
        ),
        RunStatus.PAUSED to setOf(RunStatus.RECOVERING, RunStatus.CANCELLED),
        RunStatus.COMPLETED to emptySet(),
        RunStatus.FAILED to emptySet(),
        RunStatus.CANCELLED to emptySet(),
        RunStatus.ROLLED_BACK to emptySet(),
    )

    fun canTransition(from: RunStatus, to: RunStatus): Boolean =
        from == to || to in transitions.getValue(from)

    fun requireTransition(from: RunStatus, to: RunStatus) {
        check(canTransition(from, to)) { "Invalid run transition: $from -> $to" }
    }

    private fun activeNext(vararg normal: RunStatus): Set<RunStatus> =
        normal.toSet() + setOf(
            RunStatus.RETRYING,
            RunStatus.RECOVERING,
            RunStatus.PAUSED,
            RunStatus.CANCELLED,
            RunStatus.FAILED,
        )
}
