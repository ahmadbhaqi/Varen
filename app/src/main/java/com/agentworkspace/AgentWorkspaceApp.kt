package com.agentworkspace

import android.app.Application
import android.util.Log
import com.agentworkspace.runtime.application.StartupRunRecovery
import com.agentworkspace.data.security.CredentialMigrator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AgentWorkspaceApp : Application() {
    @Inject lateinit var startupRunRecovery: StartupRunRecovery
    @Inject lateinit var credentialMigrator: CredentialMigrator

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { credentialMigrator.execute() }
                .onSuccess { report ->
                    if (report.failed > 0) {
                        Log.w(TAG, "Credential migration left ${report.failed} value(s) for retry")
                    }
                }
                .onFailure { Log.e(TAG, "Unable to migrate legacy credentials", it) }
            runCatching { startupRunRecovery.execute() }
                .onFailure { Log.e(TAG, "Unable to recover persisted agent runs", it) }
        }
    }

    private companion object {
        const val TAG = "AgentWorkspaceApp"
    }
}
