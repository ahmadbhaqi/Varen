package com.agentworkspace.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A Connection represents a link to a model source or provider source.
 * The user-facing abstraction must be Connection.
 * The user should not need to understand the backend internal provider hierarchy.
 */
data class Connection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val providerType: ProviderType,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val isEnabled: Boolean = true,
    val isHealthy: Boolean = false,
    val lastHealthCheck: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val models: List<ModelInfo> = emptyList(),
    val authState: AuthState = AuthState.NOT_AUTHENTICATED,
    val presetId: String? = null,
    val authScheme: AuthScheme = AuthScheme.BEARER,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiry: Long? = null,
) {
    val isOAuth: Boolean get() = accessToken != null || refreshToken != null
    val isTokenExpired: Boolean get() = tokenExpiry != null && System.currentTimeMillis() > tokenExpiry
    val effectiveAuthKey: String? get() = accessToken ?: apiKey
    val preset: ProviderPreset? get() = presetId?.let { ProviderPresets.byId(it) }
}

enum class ProviderType(val displayName: String) {
    OPENAI("OpenAI-compatible"),
    ANTHROPIC("Anthropic"),
    GOOGLE("Google AI"),
    CUSTOM_API_KEY("Custom (API Key + Base URL)"),
    BROWSER_LOGIN("Browser Login"),
    SESSION_BASED("Session-based"),
}

enum class AuthState {
    NOT_AUTHENTICATED,
    AUTHENTICATED,
    EXPIRED,
    ERROR,
}

/**
 * Models are first-class entities in the UI.
 */
@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val connectionId: String,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val contextSize: Int? = null,
    val isRecommended: Boolean = false,
    val recommendationReason: String? = null,
    val availabilityState: AvailabilityState = AvailabilityState.AVAILABLE,
) {
    companion object {
        fun localId(connectionId: String, providerModelId: String): String =
            "$connectionId::$providerModelId"
    }
}

@Serializable
data class ModelCapabilities(
    val chat: Boolean = true,
    val vision: Boolean = false,
    val toolUse: Boolean = false,
    val functionCalling: Boolean = false,
    val reasoning: Boolean = false,
    val streaming: Boolean = true,
    val structuredOutput: Boolean = false,
    val codeAssistance: Boolean = false,
)

enum class AvailabilityState {
    AVAILABLE, UNAVAILABLE, UNKNOWN
}
