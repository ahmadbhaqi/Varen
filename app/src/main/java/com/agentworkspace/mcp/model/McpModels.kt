package com.agentworkspace.mcp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire models for the Model Context Protocol (MCP) over streamable HTTP.
 *
 * These mirror the JSON-RPC 2.0 envelopes and the tool/resource/prompt shapes
 * returned by an MCP server such as NeedMCP (https://needmcp.com/mcp).
 */

// ---- JSON-RPC envelopes --------------------------------------------------

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: McpError? = null,
)

@Serializable
data class McpError(
    val code: Int,
    val message: String? = null,
    val data: JsonElement? = null,
)

// ---- Initialize ----------------------------------------------------------

@Serializable
data class McpInitializeResult(
    val protocolVersion: String? = null,
    val capabilities: McpCapabilities? = null,
    val serverInfo: McpServerInfo? = null,
    val instructions: String? = null,
)

@Serializable
data class McpCapabilities(
    val tools: McpCapabilitySupport? = null,
    val resources: McpCapabilitySupport? = null,
    val prompts: McpCapabilitySupport? = null,
)

@Serializable
data class McpCapabilitySupport(
    val listChanged: Boolean? = null,
)

@Serializable
data class McpServerInfo(
    val name: String? = null,
    val version: String? = null,
)

// ---- Tools ---------------------------------------------------------------

@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    @SerialName("inputSchema") val inputSchema: JsonElement? = null,
)

@Serializable
data class McpToolListResult(
    val tools: List<McpTool> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class McpToolCallResult(
    val content: List<McpContent> = emptyList(),
    val isError: Boolean? = null,
    val structuredContent: JsonElement? = null,
)

@Serializable
data class McpContent(
    val type: String? = null,
    val text: String? = null,
    val mimeType: String? = null,
    val data: String? = null,
)

// ---- Resources & Prompts (optional surfaces) -----------------------------

@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class McpResourceListResult(
    val resources: List<McpResource> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class McpPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<McpPromptArgument> = emptyList(),
)

@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean? = null,
)

@Serializable
data class McpPromptListResult(
    val prompts: List<McpPrompt> = emptyList(),
    val nextCursor: String? = null,
)

/** Aggregated server capability snapshot used by the UI. */
data class McpServerSnapshot(
    val serverInfo: McpServerInfo?,
    val protocolVersion: String?,
    val tools: List<McpTool>,
    val resources: List<McpResource>,
    val prompts: List<McpPrompt>,
)