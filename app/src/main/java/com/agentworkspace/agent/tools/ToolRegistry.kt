package com.agentworkspace.agent.tools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.agentworkspace.data.model.HistoryEntry
import com.agentworkspace.data.model.HistoryType
import com.agentworkspace.data.repository.HistoryRepository
import com.agentworkspace.model.api.ToolDefinition
import com.agentworkspace.model.api.ToolFunction
import com.agentworkspace.trust.policy.AgentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool registry: the bridge between the model and the device filesystem.
 *
 * Each tool has an OpenAI-style schema (so the model knows how to call it) and
 * an executor that performs the action against the workspace via SAF
 * (Storage Access Framework) DocumentFile trees. This is what turns
 * AgentRuntime from a chatbot into an agent that actually reads, edits and
 * creates real files.
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val historyRepository: HistoryRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Map a model tool-call name to the [AgentAction] the trust policy reasons about. */
    fun actionFor(toolName: String, args: JsonObject): AgentAction = when (toolName) {
        "read_file", "list_files" -> AgentAction.ReadFile(args.str("path") ?: "")
        "search_files" -> AgentAction.SearchFiles(args.str("query") ?: args.str("path") ?: "")
        "write_file", "edit_file" -> AgentAction.WriteFile(args.str("path") ?: "")
        "delete_file" -> AgentAction.DeleteFile(args.str("path") ?: "")
        "run_command" -> AgentAction.ExecuteCommand(args.str("command") ?: "")
        else -> AgentAction.ReadFile(toolName)
    }

    /** All tool schemas, ready to attach to a chat completion request. */
    fun schemas(): List<ToolDefinition> = listOf(
        tool("read_file", "Read the text content of a file. Returns content + size.", obj("path" to "string")),
        tool("list_files", "List files and folders in a directory. Set recursive=true to walk the tree.", obj("path" to "string", "recursive" to "boolean")),
        tool("search_files", "Search file contents for a literal or regex pattern. Returns path:line:snippet.", obj("path" to "string", "query" to "string", "is_regex" to "boolean")),
        tool("write_file", "Create or overwrite a file with the given content. Returns bytes written.", obj("path" to "string", "content" to "string", "overwrite" to "boolean")),
        tool("edit_file", "Edit a file by replacing old_text with new_text. Fails if old_text is not found.", obj("path" to "string", "old_text" to "string", "new_text" to "string")),
        tool("delete_file", "Delete a single file. Directories are not supported.", obj("path" to "string")),
        tool("run_command", "Run a shell command in the workspace. Use for builds, tests, git.", obj("command" to "string", "cwd" to "string")),
    )

    /** Execute a tool call. Returns a JSON string the model can read. */
    suspend fun execute(
        toolName: String,
        argsRaw: String,
        treeUri: Uri,
        projectId: String,
        taskId: String?,
    ): String = withContext(Dispatchers.IO) {
        val args: JsonObject = try {
            json.parseToJsonElement(argsRaw) as? JsonObject ?: JsonObject(emptyMap())
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
        try {
            val result = when (toolName) {
                "read_file" -> readFile(args, treeUri)
                "list_files" -> listFiles(args, treeUri)
                "search_files" -> searchFiles(args, treeUri)
                "write_file" -> writeFile(args, treeUri)
                "edit_file" -> editFile(args, treeUri)
                "delete_file" -> deleteFile(args, treeUri)
                else -> err("Unknown tool: $toolName")
            }
            recordHistory(projectId, taskId, toolName, true, result)
            result
        } catch (e: Exception) {
            val msg = err(e.message ?: "Tool execution failed")
            recordHistory(projectId, taskId, toolName, false, msg)
            msg
        }
    }

    private fun readFile(args: JsonObject, treeUri: Uri): String {
        val path = args.str("path") ?: return err("path is required")
        val doc = find(treeUri, path) ?: return err("File not found: $path")
        if (doc.isDirectory) return err("Path is a directory: $path")
        val content = readContent(doc) ?: return err("Could not read file: $path")
        return ok("path" to path, "size" to content.length, "content" to content)
    }

    private fun listFiles(args: JsonObject, treeUri: Uri): String {
        val path = args.str("path") ?: ""
        val recursive = args.str("recursive")?.toBoolean() ?: false
        val root = (if (path.isEmpty()) DocumentFile.fromTreeUri(context, treeUri) else find(treeUri, path))
            ?: return err("Directory not found: $path")
        if (!root.isDirectory) return err("Not a directory: $path")
        val entries = mutableListOf<JsonObject>()
        collect(root, recursive, 0, 6, entries)
        return ok("path" to path, "count" to entries.size, "entries" to JsonArray(entries))
    }

    private fun collect(dir: DocumentFile, recursive: Boolean, depth: Int, maxDepth: Int, out: MutableList<JsonObject>) {
        for (child in dir.listFiles()) {
            out.add(buildJsonObject {
                put("name", child.name ?: "")
                put("path", child.uri.toString())
                put("is_directory", child.isDirectory)
            })
            if (recursive && child.isDirectory && depth < maxDepth) {
                collect(child, true, depth + 1, maxDepth, out)
            }
        }
    }

    private fun searchFiles(args: JsonObject, treeUri: Uri): String {
        val query = args.str("query") ?: return err("query is required")
        val isRegex = args.str("is_regex")?.toBoolean() ?: false
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return err("Workspace root unavailable")
        val regex = try {
            if (isRegex) Regex(query) else Regex(Regex.escape(query))
        } catch (e: Exception) { return err("Invalid pattern: ${e.message}") }
        val matches = mutableListOf<JsonObject>()
        scanSearch(root, regex, matches, 0, 8)
        return ok("query" to query, "count" to matches.size, "matches" to JsonArray(matches))
    }

    private fun scanSearch(dir: DocumentFile, regex: Regex, out: MutableList<JsonObject>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth || out.size >= 200) return
        for (child in dir.listFiles()) {
            if (out.size >= 200) return
            if (child.isDirectory) {
                val name = child.name ?: ""
                if (name !in SKIP_DIRS) scanSearch(child, regex, out, depth + 1, maxDepth)
                continue
            }
            val content = readContent(child) ?: continue
            content.lineSequence().forEachIndexed { i, line ->
                if (regex.containsMatchIn(line) && out.size < 200) {
                    out.add(buildJsonObject {
                        put("path", child.uri.toString())
                        put("line", i + 1)
                        put("snippet", line.take(240))
                    })
                }
            }
        }
    }

    private fun writeFile(args: JsonObject, treeUri: Uri): String {
        val path = args.str("path") ?: return err("path is required")
        val content = args.str("content") ?: ""
        val overwrite = args.str("overwrite")?.toBoolean() ?: true
        val existing = find(treeUri, path)
        if (existing != null && !overwrite) return err("File exists; set overwrite=true to replace: $path")
        val target = existing ?: createFile(treeUri, path) ?: return err("Could not create file: $path")
        if (target.isDirectory) return err("Path is a directory: $path")
        writeContent(target, content)
        return ok("path" to path, "bytes" to content.length, "written" to true)
    }

    private fun editFile(args: JsonObject, treeUri: Uri): String {
        val path = args.str("path") ?: return err("path is required")
        val oldText = args.str("old_text") ?: return err("old_text is required")
        val newText = args.str("new_text") ?: ""
        val doc = find(treeUri, path) ?: return err("File not found: $path")
        if (doc.isDirectory) return err("Path is a directory: $path")
        val content = readContent(doc) ?: return err("Could not read file: $path")
        if (!content.contains(oldText)) return err("old_text not found in file: $path")
        val updated = content.replace(oldText, newText)
        writeContent(doc, updated)
        return ok("path" to path, "changed" to true, "bytes" to updated.length)
    }

    private fun deleteFile(args: JsonObject, treeUri: Uri): String {
        val path = args.str("path") ?: return err("path is required")
        val doc = find(treeUri, path) ?: return err("File not found: $path")
        if (doc.isDirectory) return err("Cannot delete a directory with this tool")
        val deleted = doc.delete()
        return ok("path" to path, "deleted" to deleted)
    }

    private fun find(treeUri: Uri, relativePath: String): DocumentFile? {
        if (relativePath.startsWith("content://")) {
            return DocumentFile.fromSingleUri(context, Uri.parse(relativePath))
        }
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

    /** Public read of a workspace-relative or content:// path, for snapshots/diffs. */
    suspend fun readFileContent(treeUri: Uri, path: String): String? = withContext(Dispatchers.IO) {
        val doc = find(treeUri, path) ?: return@withContext null
        if (doc.isDirectory) return@withContext null
        readContent(doc)
    }

    private fun readContent(doc: DocumentFile): String? = try {
        context.contentResolver.openInputStream(doc.uri)?.use { it.bufferedReader().readText() }
    } catch (e: Exception) { null }

    private fun writeContent(doc: DocumentFile, content: String) {
        context.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.write(content.toByteArray()) }
    }

    private fun recordHistory(projectId: String, taskId: String?, tool: String, success: Boolean, result: String) {
        try {
            kotlinx.coroutines.runBlocking {
                historyRepository.recordHistory(
                    HistoryEntry(
                        projectId = projectId, taskId = taskId,
                        type = HistoryType.TOOL_CALL, toolCalled = tool,
                        description = "Tool: $tool", success = success, details = result.take(500),
                    ),
                )
            }
        } catch (_: Exception) { }
    }

    private fun tool(name: String, desc: String, params: JsonObject) =
        ToolDefinition(function = ToolFunction(name = name, description = desc, parameters = params))

    private fun obj(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            for ((k, v) in pairs) put(k, buildJsonObject { put("type", v) })
        })
        put("required", JsonArray(pairs.map { JsonPrimitive(it.first) }))
    }

    private fun ok(vararg pairs: Pair<String, Any?>): String = buildJsonObject {
        for ((k, v) in pairs) {
            when (v) {
                is Number -> put(k, v)
                is Boolean -> put(k, v)
                is String -> put(k, v)
                is kotlinx.serialization.json.JsonElement -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }.toString()

    private fun err(msg: String): String = buildJsonObject { put("error", msg) }.toString()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

    companion object {
        private val SKIP_DIRS = setOf("node_modules", ".git", "build", ".gradle", "dist", "out", "target", "__pycache__", ".idea")
    }
}
