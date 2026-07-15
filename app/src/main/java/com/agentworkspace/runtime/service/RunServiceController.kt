package com.agentworkspace.runtime.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface RunLauncher {
    fun start(runId: String)
    fun resume(runId: String)
    fun pause(runId: String)
    fun cancel(runId: String)
}

@Singleton
class RunServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : RunLauncher {
    override fun start(runId: String) = dispatch(AgentRunService.ACTION_START, runId)

    override fun resume(runId: String) = dispatch(AgentRunService.ACTION_RESUME, runId)

    override fun pause(runId: String) = dispatch(AgentRunService.ACTION_PAUSE, runId)

    override fun cancel(runId: String) = dispatch(AgentRunService.ACTION_CANCEL, runId)

    private fun dispatch(action: String, runId: String) {
        val intent = Intent(context, AgentRunService::class.java)
            .setAction(action)
            .putExtra(AgentRunService.EXTRA_RUN_ID, runId)
        ContextCompat.startForegroundService(context, intent)
    }
}
