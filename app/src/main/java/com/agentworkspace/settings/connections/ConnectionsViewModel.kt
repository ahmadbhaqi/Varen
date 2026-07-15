package com.agentworkspace.settings.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ModelCapabilities
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.model.ProviderAuthFlow
import com.agentworkspace.data.model.ProviderPreset
import com.agentworkspace.data.model.ProviderPresets
import com.agentworkspace.data.model.ProviderType
import com.agentworkspace.data.repository.ConnectionRepository
import com.agentworkspace.model.api.AuthManager
import com.agentworkspace.model.api.LlmApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceCodeUiState(
    val providerName: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val status: String,
    val isComplete: Boolean = false,
    val isError: Boolean = false,
)

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val authManager: AuthManager,
    private val llmApiClient: LlmApiClient,
) : ViewModel() {

    val connections: StateFlow<List<Connection>> = connectionRepository.getAllConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showProviderPicker = MutableStateFlow(false)
    val showProviderPicker: StateFlow<Boolean> = _showProviderPicker.asStateFlow()

    private val _selectedPreset = MutableStateFlow<ProviderPreset?>(null)
    val selectedPreset: StateFlow<ProviderPreset?> = _selectedPreset.asStateFlow()

    private val _showCustomDialog = MutableStateFlow(false)
    val showCustomDialog: StateFlow<Boolean> = _showCustomDialog.asStateFlow()

    private val _customPreset = MutableStateFlow(ProviderPresets.CUSTOM_OPENAI)
    val customPreset: StateFlow<ProviderPreset> = _customPreset.asStateFlow()

    private val _manualModelConnection = MutableStateFlow<Connection?>(null)
    val manualModelConnection: StateFlow<Connection?> = _manualModelConnection.asStateFlow()

    private val _deviceCodeState = MutableStateFlow<DeviceCodeUiState?>(null)
    val deviceCodeState: StateFlow<DeviceCodeUiState?> = _deviceCodeState.asStateFlow()

    val presets: List<ProviderPreset> = ProviderPresets.all

    fun showProviderPicker() { _showProviderPicker.value = true }
    fun dismissPicker() { _showProviderPicker.value = false }
    fun selectPreset(preset: ProviderPreset) {
        if (preset.id == ProviderPresets.CUSTOM_OPENAI.id || preset.id == ProviderPresets.CUSTOM_ANTHROPIC.id) {
            showCustomConnectionDialog(preset)
            return
        }
        _selectedPreset.value = preset
        _showProviderPicker.value = false
    }
    fun clearPreset() { _selectedPreset.value = null }
    fun showCustomConnectionDialog(preset: ProviderPreset = ProviderPresets.CUSTOM_OPENAI) {
        _customPreset.value = preset
        _selectedPreset.value = null
        _showProviderPicker.value = false
        _showCustomDialog.value = true
    }
    fun dismissCustomConnectionDialog() { _showCustomDialog.value = false }
    fun showManualModelDialog(connection: Connection) { _manualModelConnection.value = connection }
    fun dismissManualModelDialog() { _manualModelConnection.value = null }
    fun dismissDeviceCodeDialog() { _deviceCodeState.value = null }
    fun openDeviceCodePage() {
        val state = _deviceCodeState.value ?: return
        authManager.launchExternalUrl(state.verificationUriComplete ?: state.verificationUri)
    }

    fun addConnectionWithApiKey(preset: ProviderPreset, apiKey: String) {
        viewModelScope.launch {
            val connection = connectionRepository.createConnection(
                name = preset.displayName,
                providerType = preset.providerType,
                baseUrl = preset.baseUrl,
                apiKey = apiKey,
            )
            connectionRepository.updatePreset(connection.id, preset.id, preset.authScheme)
            connectionRepository.updateAuthState(connection.id, AuthState.AUTHENTICATED)
            syncPresetModels(connection, preset)
            _selectedPreset.value = null
            _showProviderPicker.value = false
        }
    }

    fun addCustomConnection(preset: ProviderPreset, name: String, baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            val connection = connectionRepository.createConnection(
                name = name.ifBlank { preset.displayName },
                providerType = preset.providerType,
                baseUrl = baseUrl,
                apiKey = apiKey,
            )
            connectionRepository.updatePreset(connection.id, preset.id, preset.authScheme)
            connectionRepository.updateAuthState(connection.id, AuthState.AUTHENTICATED)
            _showCustomDialog.value = false
            _showProviderPicker.value = false
        }
    }

    fun launchBrowserLogin(preset: ProviderPreset) {
        viewModelScope.launch {
            if (preset.authFlow == ProviderAuthFlow.OAUTH_PKCE && preset.oauthConfig != null) {
                val connection = connectionRepository.createConnection(
                    name = preset.displayName,
                    providerType = preset.providerType,
                    baseUrl = preset.baseUrl,
                    apiKey = null,
                )
                connectionRepository.updatePreset(connection.id, preset.id, preset.authScheme)
                connectionRepository.updateAuthState(connection.id, AuthState.NOT_AUTHENTICATED)
                syncPresetModels(connection, preset)
                val launched = authManager.beginOAuthLogin(preset, connection.id)
                if (launched) {
                    _selectedPreset.value = null
                    _showProviderPicker.value = false
                } else {
                    connectionRepository.updateAuthState(connection.id, AuthState.ERROR)
                    authManager.launchProviderPage(preset)
                }
            } else if (preset.authFlow == ProviderAuthFlow.DEVICE_CODE && preset.deviceCodeConfig != null) {
                val connection = connectionRepository.createConnection(
                    name = preset.displayName,
                    providerType = preset.providerType,
                    baseUrl = preset.baseUrl,
                    apiKey = null,
                )
                connectionRepository.updatePreset(connection.id, preset.id, preset.authScheme)
                connectionRepository.updateAuthState(connection.id, AuthState.NOT_AUTHENTICATED)
                syncPresetModels(connection, preset)
                runCatching {
                    authManager.beginDeviceCodeLogin(preset, connection.id)
                }.onSuccess { session ->
                    if (session == null) {
                        connectionRepository.updateAuthState(connection.id, AuthState.ERROR)
                        authManager.launchProviderPage(preset)
                        return@onSuccess
                    }
                    _selectedPreset.value = null
                    _showProviderPicker.value = false
                    _deviceCodeState.value = DeviceCodeUiState(
                        providerName = session.providerName,
                        userCode = session.userCode,
                        verificationUri = session.verificationUri,
                        verificationUriComplete = session.verificationUriComplete,
                        status = "Waiting for browser authorization...",
                    )
                    val authenticated = authManager.completeDeviceCodeLogin(session)
                    _deviceCodeState.value = _deviceCodeState.value?.copy(
                        status = if (authenticated) "Connected. Subscription quota is ready for this provider." else "Authorization was not completed.",
                        isComplete = authenticated,
                        isError = !authenticated,
                    )
                }.onFailure {
                    connectionRepository.updateAuthState(connection.id, AuthState.ERROR)
                    _deviceCodeState.value = DeviceCodeUiState(
                        providerName = preset.displayName,
                        userCode = "",
                        verificationUri = preset.browserLoginUrl ?: preset.signupUrl ?: preset.baseUrl,
                        verificationUriComplete = null,
                        status = it.message ?: "Unable to start device login.",
                        isComplete = true,
                        isError = true,
                    )
                }
            } else {
                if (preset.authFlow != ProviderAuthFlow.API_KEY) {
                    val connection = connectionRepository.createConnection(
                        name = preset.displayName,
                        providerType = preset.providerType,
                        baseUrl = preset.baseUrl,
                        apiKey = null,
                    )
                    connectionRepository.updatePreset(connection.id, preset.id, preset.authScheme)
                    connectionRepository.updateAuthState(connection.id, AuthState.NOT_AUTHENTICATED)
                    syncPresetModels(connection, preset)
                    _selectedPreset.value = null
                    _showProviderPicker.value = false
                }
                authManager.launchProviderPage(preset)
            }
        }
    }

    fun addManualModel(
        connection: Connection,
        modelId: String,
        contextSize: Int?,
        vision: Boolean,
        reasoning: Boolean,
        toolUse: Boolean,
        structuredOutput: Boolean,
    ) {
        val providerModelId = modelId.trim()
        if (providerModelId.isBlank()) return
        viewModelScope.launch {
            connectionRepository.upsertModel(
                ModelInfo(
                    id = ModelInfo.localId(connection.id, providerModelId),
                    name = providerModelId,
                    connectionId = connection.id,
                    capabilities = ModelCapabilities(
                        chat = true,
                        vision = vision,
                        toolUse = toolUse,
                        functionCalling = toolUse,
                        reasoning = reasoning,
                        streaming = true,
                        structuredOutput = structuredOutput,
                        codeAssistance = true,
                    ),
                    contextSize = contextSize,
                    isRecommended = connection.models.isEmpty(),
                    recommendationReason = "Added manually",
                    availabilityState = com.agentworkspace.data.model.AvailabilityState.AVAILABLE,
                )
            )
            _manualModelConnection.value = null
        }
    }

    fun toggleConnection(id: String, enabled: Boolean) {
        viewModelScope.launch { connectionRepository.setEnabled(id, enabled) }
    }

    fun deleteConnection(id: String) {
        viewModelScope.launch { connectionRepository.deleteConnection(id) }
    }

    private val _testingId = MutableStateFlow<String?>(null)
    val testingId: StateFlow<String?> = _testingId.asStateFlow()

    /** Real connection test: call the provider, sync discovered models, mark health. */
    fun testConnection(connection: Connection) {
        viewModelScope.launch {
            _testingId.value = connection.id
            val result = llmApiClient.testConnection(connection)
            if (result.isSuccess) {
                val models = result.getOrNull().orEmpty()
                connectionRepository.updateHealth(connection.id, healthy = true)
                connectionRepository.updateAuthState(connection.id, AuthState.AUTHENTICATED)
                if (models.isNotEmpty()) connectionRepository.syncModels(connection.id, models)
            } else {
                connectionRepository.updateHealth(connection.id, healthy = false)
                connectionRepository.updateAuthState(connection.id, AuthState.ERROR)
            }
            _testingId.value = null
        }
    }

    private suspend fun syncPresetModels(connection: Connection, preset: ProviderPreset) {
        val models = preset.popularModels
            .filter { it.isNotBlank() }
            .distinct()
            .map { modelId ->
                ModelInfo(
                    id = ModelInfo.localId(connection.id, modelId),
                    name = modelId,
                    connectionId = connection.id,
                    capabilities = inferCapabilities(modelId, preset.supportsDirectRuntime),
                    isRecommended = modelId == preset.popularModels.firstOrNull(),
                    recommendationReason = "Suggested by provider catalog",
                )
            }
        if (models.isNotEmpty()) {
            connectionRepository.syncModels(connection.id, models)
        }
    }

    private fun inferCapabilities(modelId: String, executable: Boolean): ModelCapabilities {
        val lower = modelId.lowercase()
        return ModelCapabilities(
            chat = true,
            vision = lower.contains("vision") || lower.contains("vl") || lower.contains("gpt-4o") ||
                lower.contains("gemini") || lower.contains("claude") || lower.contains("grok"),
            toolUse = executable,
            functionCalling = executable,
            reasoning = lower.contains("reason") || lower.contains("thinking") || lower.contains("o1") ||
                lower.contains("o3") || lower.contains("o4") || lower.contains("gpt-5"),
            streaming = true,
            structuredOutput = executable,
            codeAssistance = true,
        )
    }
}
