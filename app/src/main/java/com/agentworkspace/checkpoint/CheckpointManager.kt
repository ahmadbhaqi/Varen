package com.agentworkspace.checkpoint

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext

import com.agentworkspace.data.model.Checkpoint
import com.agentworkspace.data.model.CheckpointFile
import com.agentworkspace.data.model.CheckpointScope
import com.agentworkspace.data.repository.CheckpointRepository
import com.agentworkspace.data.repository.HistoryRepository
import com.agentworkspace.data.repository.DiffRepository
import com.agentworkspace.data.repository.ProjectRepository
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.HistoryType
import com.agentworkspace.diff.engine.DiffEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checkpoint Manager: creates, restores, and compares checkpoints.
 *
 * Before any destructive operation (file edit, multi-file change),
 * the agent creates a checkpoint. This is the trust anchor.
 */
@Singleton
class CheckpointManager @Inject constructor(
    private val checkpointRepository: CheckpointRepository,
    @ApplicationContext private val context: Context,
    private val historyRepository: HistoryRepository,
    private val diffRepository: DiffRepository,
    private val projectRepository: ProjectRepository,
) {

    /**
     * Create a checkpoint before an agent edits files.
     * Captures current file contents so they can be restored.
     */
    suspend fun createBeforeEdit(
        projectId: String,
        taskId: String?,
        files: List<Pair<String, String>>, // (path, content)
        reason: String,
    ): Checkpoint = withContext(Dispatchers.IO) {
        val scope = if (files.size == 1) CheckpointScope.SINGLE_FILE else CheckpointScope.MULTI_FILE

        val checkpointFiles = files.map { (path, content) ->
            CheckpointFile(
                path = path,
                content = content,
                hash = sha256(content),
            )
        }

        val checkpoint = checkpointRepository.createCheckpoint(
            projectId = projectId,
            taskId = taskId,
            scope = scope,
            files = checkpointFiles,
            reason = reason,
        )

        // Record in history
        historyRepository.recordHistory(
            HistoryEntry(
                projectId = projectId,
                taskId = taskId,
                type = HistoryType.CHECKPOINT_CREATED,
                description = "Checkpoint created: ${checkpoint.reason}",
                checkpointState = checkpoint.id,
            )
        )

        checkpoint
    }

    /**
     * Rollback to a checkpoint: restore all files to their saved state.
     * Returns the list of file paths that were restored.
     */
    suspend fun rollback(checkpointId: String): List<String> = withContext(Dispatchers.IO) {
        val checkpoint = checkpointRepository.getCheckpointById(checkpointId)
            ?: error("Checkpoint not found: $checkpointId")

        val restoredFiles = checkpoint.files.map { file ->
            restoreContent(checkpoint.projectId, file)
            file.path
        }

        historyRepository.recordHistory(
            HistoryEntry(
                projectId = checkpoint.projectId,
                taskId = checkpoint.taskId,
                type = HistoryType.ROLLBACK,
                description = "Rolled back to checkpoint: ${checkpoint.reason}",
                rollbackState = checkpoint.id,
                filesTouched = restoredFiles,
            )
        )

        restoredFiles
    }

    /**
     * Compare current file content with a checkpoint version.
     * Returns a diff showing what changed since the checkpoint.
     */
    suspend fun compareWithCheckpoint(
        checkpointId: String,
        currentFiles: Map<String, String>,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val checkpoint = checkpointRepository.getCheckpointById(checkpointId)
            ?: error("Checkpoint not found: $checkpointId")

        checkpoint.files.associate { file ->
            val current = currentFiles[file.path] ?: ""
            val diff = DiffEngine.generateDiff(file.content, current, file.path)
            file.path to diff
        }
    }

    /**
     * List all checkpoints for a project, newest first.
     */
    fun getCheckpointsForProject(projectId: String) =
        checkpointRepository.getCheckpointsForProject(projectId)

    /**
     * List all checkpoints for a specific task.
     */
    fun getCheckpointsForTask(taskId: String) =
        checkpointRepository.getCheckpointsForTask(taskId)

    private suspend fun restoreContent(projectId: String, file: CheckpointFile) {
        val target = if (file.path.startsWith("content://")) {
            DocumentFile.fromSingleUri(context, Uri.parse(file.path))
        } else {
            val project = projectRepository.getProjectById(projectId).first() ?: return
            val treeUri = Uri.parse(project.path)
            find(treeUri, file.path) ?: createFile(treeUri, file.path)
        } ?: return

        if (target.isDirectory) return
        context.contentResolver.openOutputStream(target.uri, "wt")?.use {
            it.write(file.content.toByteArray())
        }
    }

    private fun find(treeUri: Uri, relativePath: String): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val parts = relativePath.trim('/').split('/').filter { it.isNotEmpty() }
        for (part in parts) {
            current = current.findFile(part) ?: return null
        }
        return current
    }

    private fun createFile(treeUri: Uri, relativePath: String): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val parts = relativePath.trim('/').split('/').filter { it.isNotEmpty() }
        for ((index, part) in parts.withIndex()) {
            val isLast = index == parts.lastIndex
            val existing = current.findFile(part)
            current = if (existing != null) {
                existing
            } else if (isLast) {
                current.createFile("application/octet-stream", part) ?: return null
            } else {
                current.createDirectory(part) ?: return null
            }
        }
        return current
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
