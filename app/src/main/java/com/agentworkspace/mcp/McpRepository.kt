package com.agentworkspace.mcp

import com.agentworkspace.data.security.CredentialVault
import com.agentworkspace.data.security.CredentialField
import com.agentworkspace.mcp.client.McpClient
import com.agentworkspace.mcp.client.McpException
import com.agentworkspace.mcp.client.text
import com.agentworkspace.mcp.di.McpOkHttp
import com.agentworkspace.mcp.model.McpServerSnapshot
import com.agentworkspace.mcp.model.McpTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-facing gateway to the NeedMCP backend.
 *
 * Owns the API key (stored encrypted in [CredentialVault]), manages the live
 * [McpClient] session, and exposes connection status + the tool catalogue to
 * the UI and to the agent runtime.
 */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class McpRepository @Inject constructor(
    private val vault: CredentialVault,
    @McpOkHttp private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private var client: McpClient? = null

    private val _status = MutableStateFlow<McpStatus>(McpStatus.Disconnected)
    val status: StateFlow<McpStatus> = _status.asStateFlow()

    private val _snapshot = MutableStateFlow<McpServerSnapshot?>(null)
    val snapshot: StateFlow<McpServerSnapshot?> = _snapshot.asStateFlow()

    private val _tools = MutableStateFlow<List<McpTool>>(emptyList())
    val tools: StateFlow<List<McpTool>> = _tools.asStateFlow()

    fun getApiKey(): String? = vault.get(NeedMcp.CONNECTION_ID, CredentialField.API_KEY)

    fun saveApiKey(key: String) {
        vault.put(NeedMcp.CONNECTION_ID, CredentialField.API_KEY, key)
    }

    fun clearApiKey() {
        vault.removeAll(NeedMcp.CONNECTION_ID)
    }

    val isConfigured: Boolean get() = !getApiKey().isNullOrBlank()

    suspend fun connect(apiKey: String? = null): Result<McpServerSnapshot> = withContext(Dispatchers.IO) {
        val key = (apiKey ?: getApiKey())?.takeIf { it.isNotBlank() }
        if (apiKey != null) saveApiKey(apiKey)
        _status.value = McpStatus.Connecting
        runCatching {
            val c = McpClient(endpoint = NeedMcp.ENDPOINT, apiKey = key, client = okHttpClient, json = json)
            val snap = c.snapshot()
            client = c
            _snapshot.value = snap
            _tools.value = snap.tools
            _status.value = McpStatus.Connected(
                serverName = snap.serverInfo?.name ?: NeedMcp.DISPLAY_NAME,
                toolCount = snap.tools.size,
            )
            snap
        }.onFailure { e ->
            _status.value = McpStatus.Error(e.message ?: "Connection failed")
        }
    }

    suspend fun refresh(): Result<McpServerSnapshot> {
        disconnect()
        return connect()
    }

    suspend fun callTool(name: String, argumentsJson: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = client ?: throw McpException("Not connected to NeedMCP")
            c.callTool(name, argumentsJson).text()
        }
    }

    fun disconnect() {
        runCatching { client?.disconnect() }
        client = null
        _status.value = McpStatus.Disconnected
        _tools.value = emptyList()
        _snapshot.value = null
    }
}

sealed interface McpStatus {
    data object Disconnected : McpStatus
    data object Connecting : McpStatus
    data class Connected(val serverName: String, val toolCount: Int) : McpStatus
    data class Error(val message: String) : McpStatus
}
