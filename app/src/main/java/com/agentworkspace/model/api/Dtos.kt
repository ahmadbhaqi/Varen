package com.agentworkspace.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// OpenAI-compatible Chat Completion Request
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    @SerialName("tool_calls") val toolCalls: List<ToolCallRequest>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction,
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonElement,
)

@Serializable
data class ToolCallRequest(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction,
)

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String,
)

// Chat Completion Response
@Serializable
data class ChatCompletionResponse(
    val id: String,
    val choices: List<ChatChoice>,
    val usage: UsageResponse? = null,
    val model: String? = null,
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessageResponse,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatMessageResponse(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallResponse>? = null,
)

@Serializable
data class ToolCallResponse(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunctionResponse,
)

@Serializable
data class ToolCallFunctionResponse(
    val name: String,
    val arguments: String,
)

@Serializable
data class UsageResponse(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

// Models List Response
@Serializable
data class ModelsListResponse(
    val data: List<ModelData>,
)

@Serializable
data class ModelData(
    val id: String,
    val ownedBy: String? = null,
)
