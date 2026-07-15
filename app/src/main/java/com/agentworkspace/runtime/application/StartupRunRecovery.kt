package com.agentworkspace.runtime.application

import com.agentworkspace.runtime.data.RunRecoveryStore
import com.agentworkspace.runtime.service.RunLauncher
import javax.inject.Inject

class StartupRunRecovery @Inject constructor(
    private val store: RunRecoveryStore,
    private val launcher: RunLauncher,
) {
    suspend fun execute() {
        store.prepareProcessRecovery().forEach { run ->
            launcher.resume(run.id)
        }
    }
}
