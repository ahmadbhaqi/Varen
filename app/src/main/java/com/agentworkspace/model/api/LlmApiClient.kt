package com.agentworkspace.model.api

import com.agentworkspace.data.model.Connection
import com.agentworkspace.data.model.ModelCapabilities
import com.agentworkspace.data.model.ModelInfo
import com.agentworkspace.data.model.ProviderTransportFormat
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.ResponseBody
import javax.inject.Singleton

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class LlmApiClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun createOkHttpClient(connection: Connection): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
            connection.preset?.headers?.forEach { (name, value) ->
                request.addHeader(name, value)
            }
            val key = connection.effectiveAuthKey ?: ""
            if (key.isNotBlank()) {
                when (connection.authScheme) {
                    com.agentworkspace.data.model.AuthScheme.BEARER ->
                        request.addHeader("Authorization", "Bearer $key")
                    com.agentworkspace.data.model.AuthScheme.X_API_KEY ->
                        request.addHeader("x-api-key", key)
                    com.agentworkspace.data.model.AuthScheme.GOOGLE_PARAM ->
                        request.addHeader("x-goog-api-key", key)
                    com.agentworkspace.data.model.AuthScheme.CUSTOM_HEADER -> {}
                }
            }
            chain.proceed(request.build())
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }

    fun createService(connection: Connection): LlmApiService {
        val baseUrl = normalizeRetrofitBaseUrl(connection.baseUrl)
        val client = createOkHttpClient(connection)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(LlmApiService::class.java)
    }

    fun getAuthHeader(connection: Connection): String =
        connection.effectiveAuthKey?.takeIf { it.isNotBlank() }?.let { connection.authScheme.prefix + it }.orEmpty()

    suspend fun testConnection(connection: Connection): Result<List<ModelInfo>> = runCatching {
        val preset = connection.preset
        if (preset != null && !isRuntimeSupported(preset.transportFormat)) {
            throw Exception("${preset.transportFormat.displayName} runtime is registered but not executable yet.")
        }
        if (preset?.transportFormat == ProviderTransportFormat.CLAUDE_NATIVE ||
            preset?.transportFormat == ProviderTransportFormat.GEMINI_NATIVE
        ) {
            return@runCatching preset.popularModels.mapIndexed { index, modelId ->
                ModelInfo(
                    id = ModelInfo.localId(connection.id, modelId),
                    name = modelId,
                    connectionId = connection.id,
                    capabilities = inferCapabilities(modelId),
                    isRecommended = index == 0,
                    recommendationReason = "Built-in native provider catalog",
                )
            }
        }
        val service = createService(connection)
        val response = service.listModels()
        if (response.isSuccessful) {
            val models = response.body()?.data ?: emptyList()
            models.map { modelData ->
                ModelInfo(
                    id = ModelInfo.localId(connection.id, modelData.id),
                    name = modelData.id,
                    connectionId = connection.id,
                    capabilities = inferCapabilities(modelData.id),
                )
            }
        } else {
            throw Exception("HTTP ${response.code()}: ${response.message()}")
        }
    }

    fun streamChat(connection: com.agentworkspace.data.model.Connection, request: ChatCompletionRequest): Flow<StreamDelta> = flow {
        val preset = connection.preset
        when (preset?.transportFormat) {
            ProviderTransportFormat.CLAUDE_NATIVE -> {
                streamClaudeChat(connection, request).collect { emit(it) }
                return@flow
            }
            ProviderTransportFormat.GEMINI_NATIVE -> {
                streamGeminiChat(connection, request).collect { emit(it) }
                return@flow
            }
            null, ProviderTransportFormat.OPENAI_COMPATIBLE -> Unit
            else -> {
                throw Exception("${preset.displayName} uses ${preset.transportFormat.displayName}; this Android runtime does not execute that transport yet.")
            }
        }
        val service = createService(connection)
        val resp = service.streamChatCompletion(request.copy(stream = true))
        if (!resp.isSuccessful) {
            val body = resp.errorBody()?.string()?.take(300) ?: resp.message()
            throw Exception("Chat stream failed code=" + resp.code() + ": " + body)
        }
        val raw: ResponseBody = resp.body() ?: throw Exception("Empty stream body")
        val source = raw.source()
        val content = StringBuilder()
        val toolArgs = mutableMapOf<Int, Pair<String, StringBuilder>>()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") break
            val jsonEl = runCatching { json.parseToJsonElement(data) }.getOrNull() ?: continue
            val obj = jsonEl as? kotlinx.serialization.json.JsonObject ?: continue
            obj["usage"]?.let { emit(StreamDelta.Usage(it.toString())) }
            val choice = (obj["choices"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? kotlinx.serialization.json.JsonObject
            val delta = choice?.get("delta") as? kotlinx.serialization.json.JsonObject ?: continue
            (delta["content"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeUnless { it is kotlinx.serialization.json.JsonNull }
                ?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
                ?.let { cc ->
                    content.append(cc)
                    emit(StreamDelta.Text(cc))
                }
            (delta["tool_calls"] as? kotlinx.serialization.json.JsonArray)?.forEach { tcEl ->
                val tc = tcEl as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val idx = (tc["index"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
                val fn = tc["function"] as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val nm = (fn["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val args = (fn["arguments"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val cur = toolArgs.getOrPut(idx) { (nm ?: "") to StringBuilder() }
                if (nm != null) toolArgs[idx] = nm to cur.second
                if (args != null) { cur.second.append(args); emit(StreamDelta.ToolCall(idx, nm, args)) }
            }
        }
        emit(StreamDelta.Done(content.toString(), toolArgs.map { (i, p) -> AggregatedToolCall(i, p.first, p.second.toString()) }))
    }

    private fun streamClaudeChat(connection: Connection, request: ChatCompletionRequest): Flow<StreamDelta> = flow {
        val url = connection.baseUrl?.takeIf { it.isNotBlank() }
            ?: throw Exception("Claude native endpoint is not configured.")
        val client = createOkHttpClient(connection)
        val body = buildClaudeRequest(request).toString().toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("anthropic-version", "2023-06-01")
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("Claude request failed code=${response.code}: ${rawBody.take(300)}")
            }
            val root = parseObject(rawBody)
            root["usage"]?.let { emit(StreamDelta.Usage(it.toString())) }
            val content = StringBuilder()
            val toolCalls = mutableListOf<AggregatedToolCall>()
            val blocks = root["content"] as? JsonArray ?: JsonArray(emptyList())
            blocks.forEach { blockEl ->
                val block = blockEl as? JsonObject ?: return@forEach
                when (block.string("type")) {
                    "text" -> {
                        val text = block.string("text").orEmpty()
                        if (text.isNotEmpty()) {
                            content.append(text)
                            emit(StreamDelta.Text(text))
                        }
                    }
                    "tool_use" -> {
                        val index = toolCalls.size
                        val name = block.string("name").orEmpty()
                        val input = block["input"]?.toString() ?: "{}"
                        toolCalls += AggregatedToolCall(index, name, input)
                        emit(StreamDelta.ToolCall(index, name, input))
                    }
                }
            }
            emit(StreamDelta.Done(content.toString(), toolCalls))
        }
    }

    private fun streamGeminiChat(connection: Connection, request: ChatCompletionRequest): Flow<StreamDelta> = flow {
        val url = geminiGenerateContentUrl(connection.baseUrl, request.model)
        val client = createOkHttpClient(connection)
        val body = buildGeminiRequest(request).toString().toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("Gemini request failed code=${response.code}: ${rawBody.take(300)}")
            }
            val root = parseObject(rawBody)
            geminiUsage(root)?.let { emit(StreamDelta.Usage(it.toString())) }
            val content = StringBuilder()
            val toolCalls = mutableListOf<AggregatedToolCall>()
            val candidates = root["candidates"] as? JsonArray ?: JsonArray(emptyList())
            val first = candidates.firstOrNull() as? JsonObject
            val parts = first?.jsonObject("content")?.get("parts") as? JsonArray ?: JsonArray(emptyList())
            parts.forEach { partEl ->
                val part = partEl as? JsonObject ?: return@forEach
                part.string("text")?.let { text ->
                    if (text.isNotEmpty()) {
                        content.append(text)
                        emit(StreamDelta.Text(text))
                    }
                }
                part.jsonObject("functionCall")?.let { call ->
                    val index = toolCalls.size
                    val name = call.string("name").orEmpty()
                    val args = call["args"]?.toString() ?: "{}"
                    toolCalls += AggregatedToolCall(index, name, args)
                    emit(StreamDelta.ToolCall(index, name, args))
                }
            }
            emit(StreamDelta.Done(content.toString(), toolCalls))
        }
    }

    private fun buildClaudeRequest(request: ChatCompletionRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("max_tokens", request.maxTokens ?: 4096)
        request.temperature?.let { put("temperature", it) }
        val system = request.messages
            .filter { it.role == "system" }
            .joinToString("\n\n") { it.content }
            .trim()
        if (system.isNotBlank()) put("system", system)
        putJsonArray("messages") {
            request.messages
                .filterNot { it.role == "system" }
                .forEach { message ->
                    when (message.role) {
                        "assistant" -> addClaudeAssistantMessage(message)
                        "tool" -> addClaudeToolResult(message)
                        else -> add(buildJsonObject {
                            put("role", "user")
                            put("content", message.content)
                        })
                    }
                }
        }
        request.tools?.takeIf { it.isNotEmpty() }?.let { tools ->
            putJsonArray("tools") {
                tools.forEach { tool ->
                    add(buildJsonObject {
                        put("name", tool.function.name)
                        put("description", tool.function.description)
                        put("input_schema", tool.function.parameters)
                    })
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addClaudeAssistantMessage(message: ChatMessage) {
        add(buildJsonObject {
            put("role", "assistant")
            putJsonArray("content") {
                if (message.content.isNotBlank()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", message.content)
                    })
                }
                message.toolCalls.orEmpty().forEach { call ->
                    add(buildJsonObject {
                        put("type", "tool_use")
                        put("id", call.id)
                        put("name", call.function.name)
                        put("input", parseToolInput(call.function.arguments))
                    })
                }
            }
        })
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addClaudeToolResult(message: ChatMessage) {
        add(buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", message.toolCallId ?: message.name ?: "tool_result")
                    put("content", message.content)
                })
            }
        })
    }

    private fun buildGeminiRequest(request: ChatCompletionRequest): JsonObject = buildJsonObject {
        val system = request.messages
            .filter { it.role == "system" }
            .joinToString("\n\n") { it.content }
            .trim()
        if (system.isNotBlank()) {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", system) })
                }
            }
        }
        putJsonArray("contents") {
            request.messages
                .filterNot { it.role == "system" }
                .forEach { message ->
                    when (message.role) {
                        "assistant" -> addGeminiAssistantMessage(message)
                        "tool" -> addGeminiToolResult(message)
                        else -> add(buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                add(buildJsonObject { put("text", message.content) })
                            }
                        })
                    }
                }
        }
        request.tools?.takeIf { it.isNotEmpty() }?.let { tools ->
            putJsonArray("tools") {
                add(buildJsonObject {
                    putJsonArray("functionDeclarations") {
                        tools.forEach { tool ->
                            add(buildJsonObject {
                                put("name", tool.function.name)
                                put("description", tool.function.description)
                                put("parameters", tool.function.parameters)
                            })
                        }
                    }
                })
            }
        }
        putJsonObject("generationConfig") {
            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("maxOutputTokens", it) }
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addGeminiAssistantMessage(message: ChatMessage) {
        add(buildJsonObject {
            put("role", "model")
            putJsonArray("parts") {
                if (message.content.isNotBlank()) {
                    add(buildJsonObject { put("text", message.content) })
                }
                message.toolCalls.orEmpty().forEach { call ->
                    add(buildJsonObject {
                        putJsonObject("functionCall") {
                            put("name", call.function.name)
                            put("args", parseToolInput(call.function.arguments))
                        }
                    })
                }
            }
        })
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addGeminiToolResult(message: ChatMessage) {
        add(buildJsonObject {
            put("role", "user")
            putJsonArray("parts") {
                add(buildJsonObject {
                    putJsonObject("functionResponse") {
                        put("name", message.name ?: "tool")
                        put("response", parseToolResponse(message.content))
                    }
                })
            }
        })
    }

    private fun normalizeRetrofitBaseUrl(baseUrl: String?): String {
        val fallback = "https://api.openai.com/v1/"
        val raw = baseUrl?.trim().orEmpty()
        if (raw.isBlank()) return fallback
        val withoutQuery = raw.substringBefore("?")
        val knownSuffixes = listOf(
            "/chat/completions",
            "/responses",
        )
        val normalized = knownSuffixes.firstNotNullOfOrNull { suffix ->
            if (withoutQuery.endsWith(suffix)) withoutQuery.removeSuffix(suffix) else null
        } ?: withoutQuery
        return if (normalized.endsWith("/")) normalized else "$normalized/"
    }

    private fun isRuntimeSupported(format: ProviderTransportFormat): Boolean =
        format == ProviderTransportFormat.OPENAI_COMPATIBLE ||
            format == ProviderTransportFormat.CLAUDE_NATIVE ||
            format == ProviderTransportFormat.GEMINI_NATIVE

    private fun parseObject(raw: String): JsonObject =
        json.parseToJsonElement(raw) as? JsonObject
            ?: throw Exception("Provider returned a non-object response")

    private fun parseToolInput(raw: String): JsonObject {
        val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        return parsed as? JsonObject ?: buildJsonObject {
            put("value", raw)
        }
    }

    private fun parseToolResponse(raw: String): JsonObject {
        val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        return parsed as? JsonObject ?: buildJsonObject {
            put("content", raw)
        }
    }

    private fun geminiGenerateContentUrl(baseUrl: String?, modelId: String): String {
        val base = baseUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "https://generativelanguage.googleapis.com/v1beta/models"
        if (base.contains(":generateContent")) return base
        val normalized = base.trimEnd('/')
        val encodedModel = modelId.replace("/", "%2F")
        return "$normalized/$encodedModel:generateContent"
    }

    private fun geminiUsage(root: JsonObject): JsonObject? {
        val usage = root.jsonObject("usageMetadata") ?: return null
        return buildJsonObject {
            put("input_tokens", usage.int("promptTokenCount") ?: 0)
            put("output_tokens", usage.int("candidatesTokenCount") ?: 0)
            put("cached_tokens", usage.int("cachedContentTokenCount") ?: 0)
            put("reasoning_tokens", usage.int("thoughtsTokenCount") ?: 0)
        }
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private fun JsonObject.jsonObject(name: String): JsonObject? =
        this[name] as? JsonObject

    private fun inferCapabilities(modelId: String): ModelCapabilities {
        val lower = modelId.lowercase()
        return ModelCapabilities(
            chat = true,
            vision = lower.contains("vision") || lower.contains("gpt-4o") || lower.contains("claude-3"),
            toolUse = lower.contains("gpt-4") || lower.contains("claude-3") || lower.contains("gemini") || lower.contains("llama-3"),
            functionCalling = lower.contains("gpt-4") || lower.contains("claude-3") || lower.contains("gemini"),
            reasoning = lower.contains("o1") || lower.contains("o3") || lower.contains("reasoner") || lower.contains("thinking"),
            streaming = true,
            structuredOutput = lower.contains("gpt-4o") || lower.contains("claude-3.5"),
            codeAssistance = true,
        )
    }
}
