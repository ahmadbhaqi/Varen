package com.agentworkspace.diff

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.DiffEntry
import com.agentworkspace.data.model.DiffStatus
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.HistoryType
import com.agentworkspace.data.repository.DiffRepository
import com.agentworkspace.data.repository.HistoryRepository
import com.agentworkspace.data.repository.ProjectRepository
import com.agentworkspace.diff.engine.DiffEngine
import com.agentworkspace.diff.engine.DiffLineDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DiffReviewState(
    val isLoading: Boolean = true,
    val diff: DiffEntry? = null,
    val lines: List<DiffLineDisplay> = emptyList(),
    val added: Int = 0,
    val removed: Int = 0,
)

/**
 * Backs the Diff review surface with real persisted changes from the
 * DiffRepository, never sample data. The user can accept or reject a
 * change and the decision is persisted through [DiffStatus].
 */
@HiltViewModel
class DiffViewModel @Inject constructor(
    private val diffRepository: DiffRepository,
    private val projectRepository: ProjectRepository,
    private val historyRepository: HistoryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DiffReviewState())
    val state: StateFlow<DiffReviewState> = _state.asStateFlow()
    private var activeProjectId: String? = null

    fun load(projectId: String, taskId: String) {
        activeProjectId = projectId
        viewModelScope.launch {
            diffRepository.getDiffsForTask(taskId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                .collect { diffs ->
                    val pending = diffs.lastOrNull { it.status == DiffStatus.PENDING }
                        ?: diffs.lastOrNull()
                    applyDiff(pending)
                }
        }
    }

    fun accept() {
        val current = _state.value.diff ?: return
        viewModelScope.launch {
            diffRepository.updateDiffStatus(current.id, DiffStatus.ACCEPTED, accepted = true)
            historyRepository.recordHistory(
                HistoryEntry(
                    projectId = current.projectId,
                    taskId = current.taskId,
                    type = HistoryType.FILE_WRITE,
                    description = "Accepted change: ${current.filePath}",
                    filesTouched = listOf(current.filePath),
                ),
            )
            _state.update { it.copy(diff = current.copy(status = DiffStatus.ACCEPTED, accepted = true)) }
        }
    }

    fun reject() {
        val current = _state.value.diff ?: return
        viewModelScope.launch {
            restoreOriginal(activeProjectId ?: current.projectId, current)
            diffRepository.updateDiffStatus(current.id, DiffStatus.REJECTED, accepted = false)
            historyRepository.recordHistory(
                HistoryEntry(
                    projectId = current.projectId,
                    taskId = current.taskId,
                    type = HistoryType.ROLLBACK,
                    description = "Rejected change and restored original: ${current.filePath}",
                    filesTouched = listOf(current.filePath),
                    rollbackState = current.id,
                ),
            )
            _state.update { it.copy(diff = current.copy(status = DiffStatus.REJECTED, accepted = false)) }
        }
    }

    private suspend fun restoreOriginal(projectId: String, diff: DiffEntry) = withContext(Dispatchers.IO) {
        val target = if (diff.filePath.startsWith("content://")) {
            DocumentFile.fromSingleUri(context, Uri.parse(diff.filePath))
        } else {
            val project = projectRepository.getProjectById(projectId).first() ?: return@withContext
            val treeUri = Uri.parse(project.path)
            find(treeUri, diff.filePath) ?: createFile(treeUri, diff.filePath)
        } ?: return@withContext

        if (!target.isDirectory) {
            context.contentResolver.openOutputStream(target.uri, "wt")?.use {
                it.write(diff.originalContent.toByteArray())
            }
        }
    }

    private fun applyDiff(entry: DiffEntry?) {
        if (entry == null) {
            _state.value = DiffReviewState(isLoading = false)
            return
        }
        val lines = DiffEngine.getDiffLines(entry.originalContent, entry.newContent)
        _state.value = DiffReviewState(
            isLoading = false,
            diff = entry,
            lines = lines,
            added = lines.count { it.type == com.agentworkspace.diff.engine.DiffLineType.ADDED },
            removed = lines.count { it.type == com.agentworkspace.diff.engine.DiffLineType.REMOVED },
        )
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
}
