package com.agentworkspace.model.api

/**
 * Streaming deltas emitted by [LlmApiClient.streamChat]. The UI renders text
 * token-by-token and accumulates tool-call arguments as they arrive.
 */
sealed class StreamDelta {
    /** A chunk of assistant text. */
    data class Text(val text: String) : StreamDelta()
    /** A fragment of a tool call (function name and/or argument chunk). */
    data class ToolCall(val index: Int, val name: String?, val argumentsChunk: String) : StreamDelta()
    /** A usage object reported mid/end stream. */
    data class Usage(val raw: String) : StreamDelta()
    /** Terminal event with the fully aggregated content and tool calls. */
    data class Done(val content: String, val toolCalls: List<AggregatedToolCall>) : StreamDelta()
}

data class AggregatedToolCall(val index: Int, val name: String, val arguments: String)
