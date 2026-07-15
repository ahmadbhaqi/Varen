package com.agentworkspace.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EditorState(
    val isLoading: Boolean = true,
    val content: String = "",
    val dirty: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

/**
 * Backs the editor with REAL file content read from the workspace via the
 * Storage Access Framework, never sample text. Saving writes back through
 * contentResolver so edits persist on disk.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    fun load(filePath: String) {
        viewModelScope.launch {
            _state.value = EditorState(isLoading = true)
            try {
                val text = withContext(Dispatchers.IO) { readUri(filePath) }
                _state.value = EditorState(isLoading = false, content = text ?: "")
            } catch (e: Exception) {
                _state.value = EditorState(isLoading = false, error = e.message ?: "Could not read file")
            }
        }
    }

    fun onContentChange(newContent: String) {
        _state.update { it.copy(content = newContent, dirty = newContent != it.content || it.dirty, saved = false) }
    }

    fun save(filePath: String) {
        viewModelScope.launch {
            try {
                val toWrite = _state.value.content
                withContext(Dispatchers.IO) { writeUri(filePath, toWrite) }
                _state.update { it.copy(dirty = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Could not save file") }
            }
        }
    }

    private fun resolveUri(filePath: String): Uri? = when {
        filePath.startsWith("content://") -> Uri.parse(filePath)
        else -> null
    }

    private fun readUri(filePath: String): String? {
        val uri = resolveUri(filePath) ?: return null
        return context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    }

    private fun writeUri(filePath: String, content: String) {
        val uri = resolveUri(filePath) ?: throw Exception("Unsupported path: $filePath")
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) }
            ?: throw Exception("Cannot open file for writing")
    }
}