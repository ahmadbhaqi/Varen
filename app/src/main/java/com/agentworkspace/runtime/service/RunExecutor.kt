package com.agentworkspace.runtime.service

fun interface RunExecutor {
    suspend fun execute(runId: String)
}
