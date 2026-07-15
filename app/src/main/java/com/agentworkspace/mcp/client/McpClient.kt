package com.agentworkspace.mcp.client

import com.agentworkspace.mcp.model.JsonRpcResponse
import com.agentworkspace.mcp.model.McpContent
import com.agentworkspace.mcp.model.McpInitializeResult
import com.agentworkspace.mcp.model.McpPrompt
import com.agentworkspace.mcp.model.McpPromptListResult
import com.agentworkspace.mcp.model.McpResource
import com.agentworkspace.mcp.model.McpResourceListResult
import com.agentworkspace.mcp.model.McpServerInfo
import com.agentworkspace.mcp.model.McpServerSnapshot
import com.agentworkspace.mcp.model.McpTool
import com.agentworkspace.mcp.model.McpToolCallResult
import com.agentworkspace.mcp.model.McpToolListResult
import com.agentworkspace.mcp.NeedMcp
import com.agentworkspace.mcp.transport.McpTransportException
import com.agentworkspace.mcp.transport.StreamableHttpTransport
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-level MCP client. Wraps the streamable-HTTP transport with the protocol
 * handshake (initialize -> notifications/initialized) and the tool/resource/
 * prompt listing + invocation surface the app actually consumes.
 */
@OptIn(ExperimentalSerializationApi::class)
class McpClient(
    private val endpoint: String = NeedMcp.ENDPOINT,
    private val apiKey: String? = null,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false },
) {
    private val transport by lazy { StreamableHttpTransport(endpoint, apiKey, client, json) }
    private val idCounter = AtomicInteger(1)

    @Volatile var serverInfo: McpServerInfo? = null
        private set

    private suspend fun rpc(
        method: String,
        params: kotlinx.serialization.json.JsonElement? = null,
        expectResult: Boolean = true,
    ): JsonRpcResponse {
        val id = if (expectResult) idCounter.getAndIncrement() else null
        val response = transport.call(
            com.agentworkspace.mcp.model.JsonRpcRequest(id = id, method = method, params = params),
        )
        response.error?.let { err ->
            throw McpException(err.message ?: "MCP error ${err.code}", err.code)
        }
        return response
    }

    /** Perform the initialize handshake. Returns the negotiated server info. */
    suspend fun connect(): McpServerInfo? {
        val initParams = buildJsonObject {
            put("protocolVersion", NeedMcp.PROTOCOL_VERSIONS.first())
            putJsonObject("capabilities") {
                putJsonObject("tools") { put("listChanged", false) }
                putJsonObject("resources") { put("listChanged", false) }
                putJsonObject("prompts") { put("listChanged", false) }
            }
            putJsonObject("clientInfo") {
                put("name", NeedMcp.CLIENT_NAME)
                put("version", NeedMcp.CLIENT_VERSION)
            }
        }
        val response = rpc("initialize", initParams)
        val result = response.result?.let { json.decodeFromJsonElement<McpInitializeResult>(it) }
        serverInfo = result?.serverInfo
        // Acknowledge the session so the server marks initialization complete.
        rpc(
            "notifications/initialized",
            buildJsonObject { put("protocolVersion", result?.protocolVersion ?: NeedMcp.PROTOCOL_VERSIONS.first()) },
            expectResult = false,
        )
        return serverInfo
    }

    suspend fun listTools(): List<McpTool> {
        val response = rpc("tools/list", buildJsonObject { })
        val parsed = response.result?.let { json.decodeFromJsonElement<McpToolListResult>(it) }
        return parsed?.tools ?: emptyList()
    }

    suspend fun callTool(name: String, arguments: kotlinx.serialization.json.JsonObject): McpToolCallResult {
        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
        val response = rpc("tools/call", params)
        return response.result?.let { json.decodeFromJsonElement<McpToolCallResult>(it) }
            ?: McpToolCallResult()
    }

    /** Convenience wrapper that accepts a raw JSON object string. */
    suspend fun callTool(name: String, argumentsJson: String): McpToolCallResult {
        val args = runCatching {
            json.parseToJsonElement(argumentsJson) as? kotlinx.serialization.json.JsonObject
                ?: kotlinx.serialization.json.JsonObject(emptyMap())
        }.getOrDefault(kotlinx.serialization.json.JsonObject(emptyMap()))
        return callTool(name, args)
    }

    suspend fun listResources(): List<McpResource> {
        val response = rpc("resources/list", buildJsonObject { })
        val parsed = response.result?.let { json.decodeFromJsonElement<McpResourceListResult>(it) }
        return parsed?.resources ?: emptyList()
    }

    suspend fun listPrompts(): List<McpPrompt> {
        val response = rpc("prompts/list", buildJsonObject { })
        val parsed = response.result?.let { json.decodeFromJsonElement<McpPromptListResult>(it) }
        return parsed?.prompts ?: emptyList()
    }

    suspend fun snapshot(): McpServerSnapshot {
        val info = serverInfo ?: connect()
        val tools = runCatching { listTools() }.getOrDefault(emptyList())
        val resources = runCatching { listResources() }.getOrDefault(emptyList())
        val prompts = runCatching { listPrompts() }.getOrDefault(emptyList())
        return McpServerSnapshot(
            serverInfo = info,
            protocolVersion = NeedMcp.PROTOCOL_VERSIONS.first(),
            tools = tools,
            resources = resources,
            prompts = prompts,
        )
    }

    fun disconnect() = transport.close()
}

class McpException(message: String, val code: Int = -1, cause: Throwable? = null) :
    Exception(message, cause)

/** Render an MCP tool-call result to a single displayable string. */
fun McpToolCallResult.text(): String {
    val parts = content.filterIsInstance<McpContent>().mapNotNull { it.text }
    return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
        ?: if (isError == true) "[tool returned an error]" else "[no output]"
}