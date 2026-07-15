package com.agentworkspace.github

import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.HistoryType
import com.agentworkspace.data.repository.HistoryRepository
import com.agentworkspace.model.api.ToolDefinition
import com.agentworkspace.model.api.ToolFunction
import com.agentworkspace.trust.policy.AgentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubToolRegistry @Inject constructor(
    private val remoteRepository: GitHubRemoteRepository,
    private val historyRepository: HistoryRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun actionFor(toolName: String, args: JsonObject): AgentAction = when (toolName) {
        "read_file", "list_files" -> AgentAction.ReadFile(args.str("path") ?: "")
        "search_files" -> AgentAction.SearchFiles(args.str("query") ?: args.str("path") ?: "")
        "write_file", "edit_file" -> AgentAction.WriteFile(args.str("path") ?: "")
        "delete_file" -> AgentAction.DeleteFile(args.str("path") ?: "")
        else -> AgentAction.ReadFile(toolName)
    }

    fun schemas(): List<ToolDefinition> = listOf(
        tool("read_file", "Read a text file from the GitHub repository branch.", obj("path" to "string")),
        tool("list_files", "List files and folders in the GitHub repository. Use recursive=true only when needed.", obj("path" to "string", "recursive" to "boolean")),
        tool("search_files", "Search GitHub repository text files for a literal or regex pattern.", obj("path" to "string", "query" to "string", "is_regex" to "boolean")),
        tool("write_file", "Create or overwrite a file in the GitHub repository branch.", obj("path" to "string", "content" to "string", "overwrite" to "boolean")),
        tool("edit_file", "Edit a file in the GitHub repository branch by replacing old_text with new_text.", obj("path" to "string", "old_text" to "string", "new_text" to "string")),
        tool("delete_file", "Delete a single file from the GitHub repository branch.", obj("path" to "string")),
    )

    suspend fun execute(
        toolName: String,
        argsRaw: String,
        project: GitHubRemoteProject,
        branch: String,
        projectId: String,
        taskId: String?,
    ): String = withContext(Dispatchers.IO) {
        val args = parseArgs(argsRaw)
        try {
            val result = when (toolName) {
                "read_file" -> readFile(project, branch, args)
                "list_files" -> listFiles(project, branch, args)
                "search_files" -> searchFiles(project, branch, args)
                "write_file" -> writeFile(project, branch, args)
                "edit_file" -> editFile(project, branch, args)
                "delete_file" -> deleteFile(project, branch, args)
                else -> err("Unknown GitHub tool: $toolName")
            }
            recordHistory(projectId, taskId, toolName, true, result)
            result
        } catch (e: Exception) {
            val result = err(e.message ?: "GitHub tool execution failed")
            recordHistory(projectId, taskId, toolName, false, result)
            result
        }
    }

    private suspend fun readFile(project: GitHubRemoteProject, branch: String, args: JsonObject): String {
        val path = args.str("path") ?: return err("path is required")
        val content = remoteRepository.readFile(project, branch, path)
        return ok("path" to path, "branch" to branch, "size" to content.length, "content" to content)
    }

    private suspend fun listFiles(project: GitHubRemoteProject, branch: String, args: JsonObject): String {
        val path = args.str("path") ?: ""
        val recursive = args.bool("recursive") ?: false
        val entries = remoteRepository.listFiles(project, branch, path, recursive).map { entry ->
            buildJsonObject {
                put("name", entry.name)
                put("path", entry.path)
                put("is_directory", entry.isDirectory)
                put("child_count", entry.childCount)
            }
        }
        return ok("path" to path, "branch" to branch, "count" to entries.size, "entries" to JsonArray(entries))
    }

    private suspend fun searchFiles(project: GitHubRemoteProject, branch: String, args: JsonObject): String {
        val query = args.str("query") ?: return err("query is required")
        val rootPath = args.str("path") ?: ""
        val matches = remoteRepository.searchFiles(
            project = project,
            branch = branch,
            rootPath = rootPath,
            query = query,
            isRegex = args.bool("is_regex") ?: false,
        )
        return ok("query" to query, "branch" to branch, "count" to matches.size, "matches" to JsonArray(matches))
    }

    private suspend fun writeFile(project: GitHubRemoteProject, branch: String, args: JsonObject): String {
        val path = args.str("path") ?: return err("path is required")
        val content = args.str("content") ?: ""
        val overwrite = args.bool("overwrite") ?: true
        remoteRepository.writeFile(
            project = project,
            branch = branch,
            path = path,
            content = content,
            overwrite = overwrite,
            message = "Varen write $path",
        )
        return ok("path" to path, "branch" to branch, "bytes" to content.length, "written" to true)
    }

    private suspend fun editFile(project: GitHubRemoteProject, branch: String, args: JsonObject): String {
        val path = args.str("path") ?: return err("path is required")
        val oldText = args.str("old_text") ?: return err("old_text is required")
        val newText = args.str("new_text") ?: ""
        remoteRepository.editFile(project, branch, path, oldText, newText)
        return ok("path" to path, "branch" to branch, "changed" to true)
    }

    private suspend fun deleteFile(project: GitHubRemoteProject, branch: String, args: JsonObject): String {
        val path = args.str("path") ?: return err("path is required")
        remoteRepository.deleteFile(project, branch, path)
        return ok("path" to path, "branch" to branch, "deleted" to true)
    }

    private fun parseArgs(argsRaw: String): JsonObject =
        runCatching { json.parseToJsonElement(argsRaw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())

    private suspend fun recordHistory(projectId: String, taskId: String?, tool: String, success: Boolean, result: String) {
        runCatching {
            historyRepository.recordHistory(
                HistoryEntry(
                    projectId = projectId,
                    taskId = taskId,
                    type = HistoryType.TOOL_CALL,
                    toolCalled = "github.$tool",
                    description = "GitHub tool: $tool",
                    success = success,
                    details = result.take(500),
                ),
            )
        }
    }

    private fun tool(name: String, desc: String, params: JsonObject): ToolDefinition =
        ToolDefinition(function = ToolFunction(name = name, description = desc, parameters = params))

    private fun obj(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            for ((k, v) in pairs) put(k, buildJsonObject { put("type", v) })
        })
        put("required", JsonArray(pairs.map { JsonPrimitive(it.first) }))
    }

    private fun ok(vararg pairs: Pair<String, Any?>): String = buildJsonObject {
        for ((key, value) in pairs) {
            when (value) {
                is Number -> put(key, value)
                is Boolean -> put(key, value)
                is String -> put(key, value)
                is JsonElement -> put(key, value)
                null -> put(key, JsonPrimitive(""))
                else -> put(key, value.toString())
            }
        }
    }.toString()

    private fun err(message: String): String = buildJsonObject { put("error", message) }.toString()

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
