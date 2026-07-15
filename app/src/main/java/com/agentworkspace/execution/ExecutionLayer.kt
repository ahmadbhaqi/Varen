package com.agentworkspace.execution

import android.content.Context
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.HistoryType
import com.agentworkspace.data.repository.HistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Execution Layer: runs shell commands and detects capabilities.
 *
 * Provides a safe, observable wrapper around command execution.
 * Falls back gracefully when tools are unavailable.
 */
@Singleton
class ExecutionLayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyRepository: HistoryRepository,
) {

    /**
     * Result of a command execution.
     */
    data class ExecutionResult(
        val command: String,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val success: Boolean,
        val durationMs: Long,
    )

    /**
     * Execute a shell command in the project directory.
     * Records the execution in history.
     */
    suspend fun execute(
        command: String,
        workingDir: String,
        projectId: String? = null,
        taskId: String? = null,
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // The workspace is exposed as a SAF (Storage Access Framework) tree
            // URI, which cannot be used as a filesystem path for ProcessBuilder.
            // Surface this as a clear, honest fallback instead of failing silently
            // with a confusing "directory does not exist" message.
            val isSafUri = workingDir.startsWith("content://") || workingDir.startsWith("content%3A")
            if (isSafUri) {
                val result = ExecutionResult(
                    command = command,
                    exitCode = -1,
                    stdout = "",
                    stderr = "Local command execution is unavailable: the workspace is a SAF-managed folder. " +
                        "Try Termux, a PC build, or continue with analysis-only verification.",
                    success = false,
                    durationMs = System.currentTimeMillis() - startTime,
                )
                recordFallback(projectId, taskId, command, result.stderr, "saf_workspace")
                return@withContext result
            }

            val dir = File(workingDir)
            if (!dir.exists()) {
                val result = ExecutionResult(
                    command = command,
                    exitCode = -1,
                    stdout = "",
                    stderr = "Working directory does not exist: $workingDir",
                    success = false,
                    durationMs = System.currentTimeMillis() - startTime,
                )
                recordFallback(projectId, taskId, command, result.stderr, "missing_workdir")
                return@withContext result
            }

            val process = ProcessBuilder("sh", "-c", command)
                .directory(dir)
                .redirectErrorStream(false)

            val proc = process.start()
            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()
            val exitCode = proc.waitFor()

            val result = ExecutionResult(
                command = command,
                exitCode = exitCode,
                stdout = stdout.trim(),
                stderr = stderr.trim(),
                success = exitCode == 0,
                durationMs = System.currentTimeMillis() - startTime,
            )

            // Record in history
            historyRepository.recordHistory(
                HistoryEntry(
                    projectId = projectId,
                    taskId = taskId,
                    type = HistoryType.COMMAND_EXECUTION,
                    description = "Executed: $command",
                    toolCalled = "shell",
                    success = result.success,
                    details = "Exit code: $exitCode, Duration: ${result.durationMs}ms",
                )
            )

            result
        } catch (e: Exception) {
            val result = ExecutionResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Unknown execution error",
                success = false,
                durationMs = System.currentTimeMillis() - startTime,
            )

            historyRepository.recordHistory(
                HistoryEntry(
                    projectId = projectId,
                    taskId = taskId,
                    type = HistoryType.ERROR,
                    description = "Execution failed: $command",
                    toolCalled = "shell",
                    success = false,
                    details = e.message,
                )
            )

            result
        }
    }

    private suspend fun recordFallback(
        projectId: String?,
        taskId: String?,
        command: String,
        message: String,
        state: String,
    ) {
        historyRepository.recordHistory(
            HistoryEntry(
                projectId = projectId,
                taskId = taskId,
                type = HistoryType.FALLBACK_TRIGGERED,
                description = message,
                toolCalled = "shell",
                success = false,
                fallbackState = state,
                details = "Command: $command",
            ),
        )
    }

    /**
     * Detect what tools/capabilities are available on this device.
     */
    suspend fun detectCapabilities(workingDir: String): Capabilities = withContext(Dispatchers.IO) {
        Capabilities(
            hasGit = checkCommand("git --version", workingDir),
            hasNode = checkCommand("node --version", workingDir),
            hasPython = checkCommand("python3 --version", workingDir) || checkCommand("python --version", workingDir),
            hasGradle = checkCommand("gradle --version", workingDir),
            hasAdb = checkCommand("adb version", workingDir),
            hasShell = true, // sh is always available on Android
        )
    }

    private fun checkCommand(command: String, workingDir: String): Boolean {
        return try {
            val dir = File(workingDir)
            if (!dir.exists()) return false
            val proc = ProcessBuilder("sh", "-c", "$command 2>/dev/null")
                .directory(dir)
                .start()
            proc.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detected device capabilities for tool selection.
     */
    data class Capabilities(
        val hasGit: Boolean,
        val hasNode: Boolean,
        val hasPython: Boolean,
        val hasGradle: Boolean,
        val hasAdb: Boolean,
        val hasShell: Boolean,
    )

    /**
     * Fallback execution: tries primary command, falls back to alternative.
     */
    suspend fun executeWithFallback(
        primaryCommand: String,
        fallbackCommand: String,
        workingDir: String,
        projectId: String? = null,
        taskId: String? = null,
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val result = execute(primaryCommand, workingDir, projectId, taskId)
        if (result.success) {
            result
        } else {
            // Record fallback
            historyRepository.recordHistory(
                HistoryEntry(
                    projectId = projectId,
                    taskId = taskId,
                    type = HistoryType.FALLBACK_TRIGGERED,
                    description = "Fallback: $primaryCommand -> $fallbackCommand",
                    fallbackState = "primary_failed",
                )
            )
            execute(fallbackCommand, workingDir, projectId, taskId)
        }
    }
}
