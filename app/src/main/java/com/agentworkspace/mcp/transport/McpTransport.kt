package com.agentworkspace.mcp.transport

import com.agentworkspace.mcp.model.JsonRpcRequest
import com.agentworkspace.mcp.model.JsonRpcResponse
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Low-level transport for MCP over streamable HTTP (the "streamable HTTP"
 * variant of the protocol: a single POST endpoint that answers with either a
 * JSON document or a text/event-stream of JSON-RPC messages).
 *
 * The transport owns the session id negotiated during `initialize` and replays
 * it on every subsequent request, as required by the spec.
 */
class StreamableHttpTransport(
    private val baseUrl: String,
    private val apiKey: String?,
    private val client: OkHttpClient,
    private val json: Json,
) {
    @Volatile private var sessionId: String? = null

    suspend fun call(request: JsonRpcRequest): JsonRpcResponse {
        val bodyText = json.encodeToString(JsonRpcRequest.serializer(), request)
        val mediaType = "application/json".toMediaType()
        val requestBuilder = Request.Builder()
            .url(baseUrl)
            .post(bodyText.toRequestBody(mediaType))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
        apiKey?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        sessionId?.let { requestBuilder.header("Mcp-Session-Id", it) }

        val call = client.newCall(requestBuilder.build())
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw McpTransportException("Network error contacting MCP server", e)
        }

        // Persist the negotiated session id for subsequent calls.
        response.header("Mcp-Session-Id")?.let { sessionId = it }

        val rawBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw McpTransportException(
                "MCP server returned HTTP ${response.code}${if (rawBody.isNotBlank()) ": $rawBody" else ""}",
            )
        }
        // Notifications (e.g. notifications/initialized) may return 202 with no body.
        if (rawBody.isBlank()) {
            return JsonRpcResponse(id = request.id)
        }
        val contentType = response.header("Content-Type").orEmpty()
        return try {
            if (contentType.contains("text/event-stream")) {
                parseSse(rawBody)
            } else {
                json.decodeFromString<JsonRpcResponse>(rawBody)
            }
        } catch (e: Exception) {
            throw McpTransportException("Failed to parse MCP response: ${e.message}", e)
        }
    }

    /**
     * SSE frames look like:
     *   event: message
     *   data: {"jsonrpc":"2.0", ...}
     *
     * We collect every `data:` line and decode the last complete JSON-RPC
     * envelope (the terminal result for our request).
     */
    private fun parseSse(raw: String): JsonRpcResponse {
        val dataLines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .toList()
        val payload = dataLines.lastOrNull().orEmpty()
        if (payload.isBlank()) return JsonRpcResponse()
        return json.decodeFromString<JsonRpcResponse>(payload)
    }

    fun close() {
        sessionId = null
    }
}

class McpTransportException(message: String, cause: Throwable? = null) :
    IOException(message, cause)