package com.agentworkspace.mcp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.mcp.McpRepository
import com.agentworkspace.mcp.McpStatus
import com.agentworkspace.mcp.NeedMcp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

enum class UiGenerateKind(
    val label: String,
    val hint: String,
    val needsStyle: Boolean,
) {
    COMPONENTS("Components", "Reusable UI building blocks", true),
    LAYOUTS("Layouts", "Page structure & placement", true),
    TOKENS("Design Tokens", "Colors, sizing, variants", true),
    DESIGN_SYSTEM("Design System", "Typography & color docs", true),
    WIREFRAMES("Wireframes", "Screen blueprints", false),
}

data class McpStyleItem(
    val name: String,
    val slug: String,
)

data class McpGenerateResult(
    val kind: UiGenerateKind,
    val text: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
@HiltViewModel
class McpViewModel @Inject constructor(
    private val repository: McpRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    val status: StateFlow<McpStatus> = repository.status

    private val _apiKeyInput = MutableStateFlow(repository.getApiKey().orEmpty())
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _styles = MutableStateFlow<List<McpStyleItem>>(emptyList())
    val styles: StateFlow<List<McpStyleItem>> = _styles.asStateFlow()

    private val _selectedStyle = MutableStateFlow<String?>(null)
    val selectedStyle: StateFlow<String?> = _selectedStyle.asStateFlow()

    private val _generateResult = MutableStateFlow<McpGenerateResult?>(null)
    val generateResult: StateFlow<McpGenerateResult?> = _generateResult.asStateFlow()

    init {
        if (repository.isConfigured) {
            viewModelScope.launch {
                repository.connect().onSuccess { loadStyles() }
            }
        }
    }

    fun onApiKeyChange(value: String) {
        _apiKeyInput.value = value
    }

    fun connect() {
        viewModelScope.launch {
            repository.connect(_apiKeyInput.value).onSuccess { loadStyles() }
        }
    }

    fun disconnect() {
        repository.disconnect()
        _apiKeyInput.value = repository.getApiKey().orEmpty()
        _styles.value = emptyList()
        _selectedStyle.value = null
        _generateResult.value = null
    }

    fun loadStyles() {
        viewModelScope.launch {
            repository.callTool("get-styles-tool", """{"limit":60}""")
                .onSuccess { _styles.value = parseStyles(it) }
        }
    }

    private fun parseStyles(text: String): List<McpStyleItem> {
        return runCatching {
            val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
            val data = root["data"]?.jsonArray ?: return emptyList()
            data.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.content
                val slug = o["slug"]?.jsonPrimitive?.content
                if (name != null && slug != null) McpStyleItem(name, slug) else null
            }
        }.getOrDefault(emptyList())
    }

    fun selectStyle(slug: String?) {
        viewModelScope.launch {
            val arg = if (slug == null) """{"selected":"all"}""" else """{"selected":"$slug"}"""
            repository.callTool("select-style-tool", arg)
            _selectedStyle.value = slug
        }
    }

    fun generate(kind: UiGenerateKind) {
        val slug = _selectedStyle.value
        if (kind.needsStyle && slug == null) {
            _generateResult.value = McpGenerateResult(kind, error = "Select a style first")
            return
        }
        val (tool, args) = when (kind) {
            UiGenerateKind.COMPONENTS ->
                "get-components-tool" to """{"styleSlug":"$slug","types":"all"}"""
            UiGenerateKind.LAYOUTS ->
                "get-layouts-tool" to """{"styleSlug":"$slug","categories":"all"}"""
            UiGenerateKind.TOKENS ->
                "get-style-tokens-tool" to """{"styleSlug":"$slug","category":"all","mode":"all"}"""
            UiGenerateKind.DESIGN_SYSTEM ->
                "get-design-system-tool" to """{"styleSlug":"$slug"}"""
            UiGenerateKind.WIREFRAMES ->
                "get-wireframes-tool" to """{"limit":12}"""
        }
        viewModelScope.launch {
            _generateResult.value = McpGenerateResult(kind, isLoading = true)
            repository.callTool(tool, args)
                .onSuccess { _generateResult.value = McpGenerateResult(kind, text = it) }
                .onFailure { _generateResult.value = McpGenerateResult(kind, error = it.message ?: "Generation failed") }
        }
    }
}