package com.agentworkspace

import android.app.Application
import android.util.Log
import com.agentworkspace.runtime.application.StartupRunRecovery
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AgentWorkspaceApp : Application() {
    @Inject lateinit var startupRunRecovery: StartupRunRecovery

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { startupRunRecovery.execute() }
                .onFailure { Log.e(TAG, "Unable to recover persisted agent runs", it) }
        }
    }

    private companion object {
        const val TAG = "AgentWorkspaceApp"
    }
}
