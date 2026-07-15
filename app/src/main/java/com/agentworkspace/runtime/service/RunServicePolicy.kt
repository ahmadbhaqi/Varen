package com.agentworkspace.runtime.service

import com.agentworkspace.runtime.domain.RunStatus

object RunServicePolicy {
    fun initialStatus(action: String?): RunStatus = when (action) {
        AgentRunService.ACTION_RESUME -> RunStatus.RECOVERING
        AgentRunService.ACTION_PAUSE -> RunStatus.PAUSED
        AgentRunService.ACTION_CANCEL -> RunStatus.CANCELLED
        else -> RunStatus.QUEUED
    }

    fun keepsServiceActive(status: RunStatus): Boolean =
        !status.isTerminal && status != RunStatus.PAUSED
}
