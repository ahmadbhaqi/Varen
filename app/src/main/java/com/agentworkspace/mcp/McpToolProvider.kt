package com.agentworkspace.mcp

import com.agentworkspace.model.api.ToolDefinition
import com.agentworkspace.model.api.ToolFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges NeedMCP's remote tool catalogue into the agent's local tool surface.
 *
 * MCP tool names are namespaced with `mcp__` (OpenAI tool names only allow
 * `[a-zA-Z0-9_-]`, so a dot separator is not valid) and routed back to the
 * NeedMCP server at execution time. This lets the model call hosted MCP tools
 * exactly like the built-in filesystem/command tools.
 */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class McpToolProvider @Inject constructor(
    private val repository: McpRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    fun schemas(): List<ToolDefinition> {
        val tools = repository.tools.value
        if (tools.isEmpty()) return emptyList()
        return tools.map { tool ->
            val params = tool.inputSchema ?: buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
            ToolDefinition(
                function = ToolFunction(
                    name = "mcp__${tool.name}",
                    description = tool.description ?: "NeedMCP tool: ${tool.name}",
                    parameters = params,
                ),
            )
        }
    }

    fun owns(toolName: String): Boolean = toolName.startsWith("mcp__")

    suspend fun execute(
        toolName: String,
        argsRaw: String,
        projectId: String,
        taskId: String?,
    ): String = withContext(Dispatchers.IO) {
        if (!owns(toolName)) return@withContext err("Not an MCP tool: $toolName")
        val original = toolName.removePrefix("mcp__")
        runCatching {
            val result = repository.callTool(original, argsRaw)
                .getOrElse { return@withContext err(it.message ?: "NeedMCP call failed") }
            ok(
                "tool" to original,
                "via" to NeedMcp.DISPLAY_NAME,
                "result" to result,
            )
        }.getOrDefault(err("NeedMCP execution failed"))
    }

    private fun ok(vararg pairs: Pair<String, String>): String = buildJsonObject {
        put("ok", true)
        for ((k, v) in pairs) put(k, v)
    }.toString()

    private fun err(message: String): String = buildJsonObject {
        put("ok", false)
        put("error", message)
    }.toString()
}