package com.agentworkspace.data.model

/**
 * Android-native provider catalog based on direct upstream provider metadata.
 *
 * The app must not depend on a local routing server. These presets describe
 * direct upstream connections: endpoint shape, auth style, provider pages, and
 * static model hints. Runtime support can grow by transport format without
 * leaking provider infrastructure into the UI.
 */
enum class AuthScheme(val headerName: String, val prefix: String) {
    BEARER("Authorization", "Bearer "),
    X_API_KEY("x-api-key", ""),
    GOOGLE_PARAM("x-goog-api-key", ""),
    CUSTOM_HEADER("", ""),
}

enum class ProviderTransportFormat(val displayName: String) {
    OPENAI_COMPATIBLE("OpenAI-compatible"),
    CLAUDE_NATIVE("Claude native"),
    GEMINI_NATIVE("Gemini native"),
    OPENAI_RESPONSES("OpenAI Responses"),
    OLLAMA("Ollama"),
    CURSOR("Cursor"),
    ANTIGRAVITY("Antigravity"),
    COMMAND_CODE("Command Code"),
    VERTEX("Vertex AI"),
}

enum class ProviderAuthFlow(val displayName: String) {
    API_KEY("API key"),
    OAUTH_PKCE("Browser OAuth"),
    DEVICE_CODE("Device code"),
    BROWSER_TOKEN("Browser token"),
    COOKIE("Browser cookie"),
    NO_AUTH("No auth"),
    CUSTOM("Custom"),
}

enum class TokenExchangeEncoding {
    FORM_URLENCODED,
    JSON,
}

data class OAuthConfig(
    val clientId: String,
    val authorizeUrl: String,
    val tokenUrl: String,
    val scopes: List<String> = emptyList(),
    val scope: String? = null,
    val codeChallengeMethod: String = "S256",
    val tokenExchangeEncoding: TokenExchangeEncoding = TokenExchangeEncoding.FORM_URLENCODED,
    val extraAuthParams: Map<String, String> = emptyMap(),
    /**
     * Redirect URI registered with the provider. CLI-derived clients (e.g.
     * Codex) only accept a loopback redirect, so the app binds a local server
     * to capture the callback. null falls back to the app deep-link scheme.
     */
    val redirectUri: String? = null,
)

enum class DeviceCodePostExchange {
    NONE,
    GITHUB_COPILOT_TOKEN,
}

data class DeviceCodeConfig(
    val clientId: String,
    val deviceCodeUrl: String,
    val tokenUrl: String,
    val scope: String? = null,
    val scopes: List<String> = emptyList(),
    val codeChallengeMethod: String? = null,
    val extraDeviceParams: Map<String, String> = emptyMap(),
    val extraTokenParams: Map<String, String> = emptyMap(),
    val postExchange: DeviceCodePostExchange = DeviceCodePostExchange.NONE,
    val postExchangeUrl: String? = null,
    val postExchangeHeaders: Map<String, String> = emptyMap(),
)

data class ProviderPreset(
    val id: String,
    val displayName: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val authScheme: AuthScheme,
    val supportsBrowserLogin: Boolean,
    val supportsApiKey: Boolean,
    val description: String,
    val category: ProviderCategory,
    val accentColor: Long,
    val popularModels: List<String>,
    val browserLoginUrl: String? = null,
    val apiKeyUrl: String? = null,
    val signupUrl: String? = null,
    val upstreamId: String = id,
    val transportFormat: ProviderTransportFormat = ProviderTransportFormat.OPENAI_COMPATIBLE,
    val authFlow: ProviderAuthFlow = ProviderAuthFlow.API_KEY,
    val serviceKinds: List<String> = listOf("llm"),
    val headers: Map<String, String> = emptyMap(),
    val supportsDirectRuntime: Boolean = transportFormat in setOf(
        ProviderTransportFormat.OPENAI_COMPATIBLE,
        ProviderTransportFormat.CLAUDE_NATIVE,
        ProviderTransportFormat.GEMINI_NATIVE,
    ),
    val oauthConfig: OAuthConfig? = null,
    val deviceCodeConfig: DeviceCodeConfig? = null,
)

enum class ProviderCategory(val displayName: String) {
    OFFICIAL("Official"),
    ROUTER("Aggregator"),
    OPEN_SOURCE("Open Source"),
    SUBSCRIPTION("Subscription"),
    FREE("Free Tier"),
    CUSTOM("Custom"),
}

object ProviderPresets {
    private fun openAiProvider(
        id: String,
        name: String,
        baseUrl: String,
        description: String,
        category: ProviderCategory,
        color: Long,
        models: List<String>,
        apiKeyUrl: String? = null,
        signupUrl: String? = null,
        headers: Map<String, String> = emptyMap(),
        serviceKinds: List<String> = listOf("llm"),
    ) = ProviderPreset(
        id = id,
        displayName = name,
        providerType = ProviderType.OPENAI,
        baseUrl = baseUrl,
        authScheme = AuthScheme.BEARER,
        supportsBrowserLogin = apiKeyUrl != null || signupUrl != null,
        supportsApiKey = true,
        description = description,
        category = category,
        accentColor = color,
        popularModels = models,
        browserLoginUrl = apiKeyUrl ?: signupUrl,
        apiKeyUrl = apiKeyUrl,
        signupUrl = signupUrl,
        headers = headers,
        serviceKinds = serviceKinds,
    )

    private fun nativeProvider(
        id: String,
        name: String,
        type: ProviderType,
        baseUrl: String,
        format: ProviderTransportFormat,
        authScheme: AuthScheme,
        flow: ProviderAuthFlow,
        description: String,
        category: ProviderCategory,
        color: Long,
        models: List<String>,
        browserUrl: String? = null,
        apiKeyUrl: String? = null,
        signupUrl: String? = null,
        supportsApiKey: Boolean = flow == ProviderAuthFlow.API_KEY,
        serviceKinds: List<String> = listOf("llm"),
        headers: Map<String, String> = emptyMap(),
        oauthConfig: OAuthConfig? = null,
        deviceCodeConfig: DeviceCodeConfig? = null,
    ) = ProviderPreset(
        id = id,
        displayName = name,
        providerType = type,
        baseUrl = baseUrl,
        authScheme = authScheme,
        supportsBrowserLogin = browserUrl != null || apiKeyUrl != null || signupUrl != null,
        supportsApiKey = supportsApiKey,
        description = description,
        category = category,
        accentColor = color,
        popularModels = models,
        browserLoginUrl = browserUrl ?: apiKeyUrl ?: signupUrl,
        apiKeyUrl = apiKeyUrl,
        signupUrl = signupUrl,
        transportFormat = format,
        authFlow = flow,
        serviceKinds = serviceKinds,
        headers = headers,
        oauthConfig = oauthConfig,
        deviceCodeConfig = deviceCodeConfig,
    )

    val CUSTOM_OPENAI = openAiProvider(
        id = "custom-openai-compatible",
        name = "Custom OpenAI-compatible",
        baseUrl = "",
        description = "Use any OpenAI-compatible API by entering a base URL and key.",
        category = ProviderCategory.CUSTOM,
        color = 0xFF64748B,
        models = emptyList(),
    )

    val CUSTOM_ANTHROPIC = nativeProvider(
        id = "custom-anthropic-compatible",
        name = "Custom Anthropic-compatible",
        type = ProviderType.ANTHROPIC,
        baseUrl = "",
        format = ProviderTransportFormat.CLAUDE_NATIVE,
        authScheme = AuthScheme.X_API_KEY,
        flow = ProviderAuthFlow.CUSTOM,
        description = "Use a Claude/Anthropic-compatible endpoint with x-api-key auth.",
        category = ProviderCategory.CUSTOM,
        color = 0xFFD97757,
        models = emptyList(),
    )

    val all: List<ProviderPreset> = listOf(
        openAiProvider(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            description = "Official OpenAI API, including GPT and reasoning models.",
            category = ProviderCategory.OFFICIAL,
            color = 0xFF10A37F,
            models = listOf("gpt-5.4", "gpt-5.4-mini", "gpt-5", "gpt-4o", "o3"),
            apiKeyUrl = "https://platform.openai.com/api-keys",
            signupUrl = "https://platform.openai.com",
            serviceKinds = listOf("llm", "embedding", "tts", "stt", "image", "imageToText", "webSearch"),
        ),
        nativeProvider(
            id = "anthropic",
            name = "Anthropic",
            type = ProviderType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1/messages",
            format = ProviderTransportFormat.CLAUDE_NATIVE,
            authScheme = AuthScheme.X_API_KEY,
            flow = ProviderAuthFlow.API_KEY,
            description = "Official Claude API. Stored directly; native Claude runtime support is a separate backend path.",
            category = ProviderCategory.OFFICIAL,
            color = 0xFFD97757,
            models = listOf("claude-sonnet-4-20250514", "claude-opus-4-20250514", "claude-3-5-sonnet-20241022"),
            apiKeyUrl = "https://console.anthropic.com/settings/keys",
            signupUrl = "https://console.anthropic.com",
            serviceKinds = listOf("llm", "imageToText"),
        ),
        openAiProvider(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/chat/completions",
            description = "Aggregator with many OpenAI-compatible hosted models.",
            category = ProviderCategory.ROUTER,
            color = 0xFFF97316,
            models = listOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet", "google/gemini-2.5-pro"),
            apiKeyUrl = "https://openrouter.ai/settings/keys",
            signupUrl = "https://openrouter.ai",
            headers = mapOf("HTTP-Referer" to "https://varen.local", "X-Title" to "Varen"),
            serviceKinds = listOf("llm", "embedding", "tts", "imageToText"),
        ),
        openAiProvider("groq", "Groq", "https://api.groq.com/openai/v1/chat/completions", "Fast OpenAI-compatible inference for Llama, Qwen, and GPT-OSS.", ProviderCategory.OFFICIAL, 0xFFF55036, listOf("llama-3.3-70b-versatile", "qwen/qwen3-32b", "openai/gpt-oss-120b"), "https://console.groq.com/keys", "https://groq.com", serviceKinds = listOf("llm", "imageToText", "stt")),
        openAiProvider("mistral", "Mistral", "https://api.mistral.ai/v1/chat/completions", "Mistral Large, Codestral, and Mistral hosted models.", ProviderCategory.OFFICIAL, 0xFFFF7000, listOf("mistral-large-latest", "codestral-latest", "mistral-medium-latest"), "https://console.mistral.ai/api-keys", "https://mistral.ai", serviceKinds = listOf("llm", "imageToText", "embedding")),
        openAiProvider("deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions", "DeepSeek chat and reasoning models.", ProviderCategory.OFFICIAL, 0xFF4D6BFE, listOf("deepseek-v4-pro", "deepseek-v4-flash", "deepseek-chat", "deepseek-reasoner"), "https://platform.deepseek.com/api_keys", "https://deepseek.com"),
        openAiProvider("xai", "xAI (Grok)", "https://api.x.ai/v1/chat/completions", "Grok models through xAI's OpenAI-compatible API.", ProviderCategory.OFFICIAL, 0xFF111827, listOf("grok-4", "grok-4-fast-reasoning", "grok-code-fast-1", "grok-3"), "https://console.x.ai", "https://x.ai", serviceKinds = listOf("llm", "imageToText", "webSearch", "image")),
        openAiProvider("together", "Together AI", "https://api.together.xyz/v1/chat/completions", "Hosted open-source models with OpenAI-compatible chat.", ProviderCategory.OPEN_SOURCE, 0xFF0F6FFF, listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "deepseek-ai/DeepSeek-R1", "Qwen/Qwen3-235B-A22B"), "https://api.together.xyz/settings/api-keys", "https://www.together.ai", serviceKinds = listOf("llm", "embedding")),
        openAiProvider("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1/chat/completions", "Fireworks hosted open models.", ProviderCategory.OPEN_SOURCE, 0xFFE11D48, listOf("accounts/fireworks/models/deepseek-v3p1", "accounts/fireworks/models/llama-v3p3-70b-instruct", "accounts/fireworks/models/qwen3-235b-a22b"), "https://fireworks.ai/account/api-keys", "https://fireworks.ai", serviceKinds = listOf("llm", "embedding")),
        openAiProvider("cerebras", "Cerebras", "https://api.cerebras.ai/v1/chat/completions", "Cerebras cloud models via OpenAI-compatible chat.", ProviderCategory.OFFICIAL, 0xFFFF6B00, listOf("gpt-oss-120b", "zai-glm-4.7", "llama-3.3-70b"), "https://cloud.cerebras.ai/platform", "https://www.cerebras.ai"),
        openAiProvider("cohere", "Cohere", "https://api.cohere.ai/v1/chat/completions", "Command models through Cohere's OpenAI-compatible surface.", ProviderCategory.OFFICIAL, 0xFF39594D, listOf("command-r-plus-08-2024", "command-r-08-2024", "command-a-03-2025"), "https://dashboard.cohere.com/api-keys", "https://cohere.com"),
        openAiProvider("perplexity", "Perplexity", "https://api.perplexity.ai/chat/completions", "Perplexity Sonar search and answer models.", ProviderCategory.OFFICIAL, 0xFF20B8CD, listOf("sonar-pro", "sonar"), "https://www.perplexity.ai/settings/api", "https://www.perplexity.ai", serviceKinds = listOf("llm", "webSearch")),
        openAiProvider("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1/chat/completions", "NVIDIA hosted model endpoints.", ProviderCategory.FREE, 0xFF76B900, listOf("minimaxai/minimax-m3", "z-ai/glm-5.2", "deepseek-ai/deepseek-v4-pro"), "https://build.nvidia.com/settings/api-keys", "https://developer.nvidia.com/nim", serviceKinds = listOf("llm", "tts", "embedding")),
        openAiProvider("nebius", "Nebius AI", "https://api.studio.nebius.ai/v1/chat/completions", "Nebius Studio OpenAI-compatible models.", ProviderCategory.OPEN_SOURCE, 0xFF00B3FF, listOf("meta-llama/Llama-3.3-70B-Instruct"), "https://studio.nebius.com/settings/api-keys", "https://nebius.com", serviceKinds = listOf("llm", "embedding")),
        openAiProvider("siliconflow", "SiliconFlow", "https://api.siliconflow.com/v1/chat/completions", "SiliconFlow hosted open-source model catalog.", ProviderCategory.OPEN_SOURCE, 0xFF111827, listOf("deepseek-ai/DeepSeek-V4-Pro", "deepseek-ai/DeepSeek-V4-Flash", "Qwen/Qwen3-Coder"), "https://cloud.siliconflow.com/account/ak", "https://cloud.siliconflow.com"),
        openAiProvider("venice", "Venice AI", "https://api.venice.ai/api/v1/chat/completions", "Venice AI privacy-focused OpenAI-compatible models.", ProviderCategory.OFFICIAL, 0xFF7C3AED, listOf("venice-uncensored-1-2", "zai-org-glm-5", "qwen3-coder-480b-a35b-instruct-turbo"), "https://venice.ai/settings/api", "https://venice.ai", serviceKinds = listOf("llm", "embedding", "image")),
        openAiProvider("vercel-ai-gateway", "Vercel AI Gateway", "https://ai-gateway.vercel.sh/v1/chat/completions", "Vercel AI Gateway as a direct OpenAI-compatible provider.", ProviderCategory.ROUTER, 0xFF000000, listOf("openai/gpt-4o-mini", "anthropic/claude-3-5-sonnet-latest"), "https://vercel.com/dashboard/~/ai-gateway", "https://vercel.com/ai-gateway", serviceKinds = listOf("llm", "embedding", "image", "imageToText", "webSearch")),
        openAiProvider("alicode", "Alibaba", "https://coding.dashscope.aliyuncs.com/v1/chat/completions", "Alibaba DashScope coding models.", ProviderCategory.OFFICIAL, 0xFFFF6A00, listOf("qwen3.5-plus", "kimi-k2.5", "glm-5", "MiniMax-M2.5"), "https://bailian.console.aliyun.com/?apiKey=1", "https://bailian.console.aliyun.com"),
        openAiProvider("alicode-intl", "Alibaba Intl", "https://coding-intl.dashscope.aliyuncs.com/v1/chat/completions", "Alibaba international model studio coding endpoints.", ProviderCategory.OFFICIAL, 0xFFFF6A00, listOf("qwen3.5-plus", "kimi-k2.5", "glm-5"), "https://modelstudio.console.alibabacloud.com/?apiKey=1", "https://modelstudio.console.alibabacloud.com"),
        openAiProvider("glm-cn", "GLM (China)", "https://open.bigmodel.cn/api/coding/paas/v4/chat/completions", "GLM coding models through BigModel OpenAI-compatible API.", ProviderCategory.OFFICIAL, 0xFF1D4ED8, listOf("glm-5.2", "glm-5.1", "glm-5", "glm-4.7"), "https://open.bigmodel.cn/usercenter/apikeys", "https://open.bigmodel.cn"),
        openAiProvider("blackbox", "Blackbox AI", "https://api.blackbox.ai/v1/chat/completions", "Blackbox AI model router with OpenAI-compatible chat.", ProviderCategory.ROUTER, 0xFF111827, listOf("claude-fable-5", "claude-opus-4.8", "claude-sonnet-4.6", "gpt-5.5"), "https://www.blackbox.ai/api-management", "https://blackbox.ai"),
        openAiProvider("byteplus", "BytePlus ModelArk", "https://ark.ap-southeast.bytepluses.com/api/coding/v3/chat/completions", "BytePlus ModelArk coding endpoints.", ProviderCategory.FREE, 0xFF2563EB, listOf("seed-2-0-pro-260328", "seed-2-0-code-preview-260328", "seed-2-0-mini-260215"), "https://console.byteplus.com/ark/region:ark+ap-southeast-1/apiKey", "https://console.byteplus.com/ark"),
        openAiProvider("volcengine-ark", "Volcengine Ark", "https://ark.cn-beijing.volces.com/api/coding/v3/chat/completions", "Volcengine Ark coding endpoints.", ProviderCategory.OFFICIAL, 0xFF2563EB, listOf("Doubao-Seed-2.0-Code", "Doubao-Seed-2.0-pro", "Doubao-Seed-Code"), "https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey", "https://ark.cn-beijing.volces.com"),
        openAiProvider("opencode-go", "OpenCode Go", "https://opencode.ai/zen/go/v1/chat/completions", "OpenCode Go hosted coding models.", ProviderCategory.OPEN_SOURCE, 0xFF0EA5E9, listOf("glm-5.2", "glm-5.1", "kimi-k2.7-code", "kimi-k2.6"), "https://opencode.ai/auth", "https://opencode.ai/auth"),
        openAiProvider("cloudflare-ai", "Cloudflare Workers AI", "https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/v1/chat/completions", "Workers AI. Replace {accountId} in the base URL for your account.", ProviderCategory.FREE, 0xFFF38020, listOf("@cf/meta/llama-3.2-3b-instruct", "@cf/meta/llama-3.1-8b-instruct-fp8-fast"), "https://dash.cloudflare.com/profile/api-tokens", "https://developers.cloudflare.com/workers-ai/"),
        openAiProvider("xiaomi-mimo", "Xiaomi MiMo", "https://api.xiaomimimo.com/v1/chat/completions", "Xiaomi MiMo OpenAI-compatible models.", ProviderCategory.OFFICIAL, 0xFFFF6900, listOf("mimo-v2.5-pro", "mimo-v2.5", "mimo-v2-omni", "mimo-v2-flash"), "https://xiaomimimo.com", "https://xiaomimimo.com"),
        openAiProvider("xiaomi-tokenplan", "Xiaomi MiMo Token Plan", "https://token-plan-sgp.xiaomimimo.com/v1/chat/completions", "Xiaomi MiMo token-plan endpoint.", ProviderCategory.OFFICIAL, 0xFFFF6900, listOf("mimo-v2.5-pro", "mimo-v2.5-pro-claude", "mimo-v2.5", "mimo-v2-pro"), "https://mimo.xiaomi.com", "https://mimo.xiaomi.com"),

        nativeProvider(
            id = "google-gemini",
            name = "Google Gemini",
            type = ProviderType.GOOGLE,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/models",
            format = ProviderTransportFormat.GEMINI_NATIVE,
            authScheme = AuthScheme.GOOGLE_PARAM,
            flow = ProviderAuthFlow.API_KEY,
            description = "Gemini native API with direct Android runtime support.",
            category = ProviderCategory.FREE,
            color = 0xFF4285F4,
            models = listOf("gemini-3.1-pro-preview", "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"),
            apiKeyUrl = "https://aistudio.google.com/app/apikey",
            signupUrl = "https://ai.google.dev",
            serviceKinds = listOf("llm", "embedding", "image", "imageToText", "webSearch", "tts", "stt"),
        ),
        nativeProvider(
            id = "claude-code",
            name = "Claude Code",
            type = ProviderType.BROWSER_LOGIN,
            baseUrl = "https://api.anthropic.com/v1/messages",
            format = ProviderTransportFormat.CLAUDE_NATIVE,
            authScheme = AuthScheme.BEARER,
            flow = ProviderAuthFlow.OAUTH_PKCE,
            description = "Claude subscription login using browser OAuth and direct Claude-native transport.",
            category = ProviderCategory.SUBSCRIPTION,
            color = 0xFFD97757,
            models = listOf("claude-opus-4-8", "claude-sonnet-4-6", "claude-haiku-4-5-20251001"),
            browserUrl = "https://claude.ai",
            signupUrl = "https://claude.ai",
            supportsApiKey = false,
            headers = mapOf(
                "anthropic-beta" to "claude-code-20250219,oauth-2025-04-20,interleaved-thinking-2025-05-14,context-management-2025-06-27,prompt-caching-scope-2026-01-05,advanced-tool-use-2025-11-20,effort-2025-11-24,structured-outputs-2025-12-15,fast-mode-2026-02-01,redact-thinking-2026-02-12,token-efficient-tools-2026-03-28",
            ),
            oauthConfig = OAuthConfig(
                clientId = "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
                authorizeUrl = "https://claude.ai/oauth/authorize",
                tokenUrl = "https://api.anthropic.com/v1/oauth/token",
                scopes = listOf("org:create_api_key", "user:profile", "user:inference"),
                tokenExchangeEncoding = TokenExchangeEncoding.JSON,
            ),
        ),
        // Subscription provider metadata adapted from 9Router's MIT-licensed open-sse registry.
        nativeProvider(
            "openai-codex",
            "OpenAI Codex",
            ProviderType.BROWSER_LOGIN,
            "https://chatgpt.com/backend-api/codex/responses",
            ProviderTransportFormat.OPENAI_RESPONSES,
            AuthScheme.BEARER,
            ProviderAuthFlow.OAUTH_PKCE,
            "Codex account OAuth metadata from the provider registry. Runtime adapter is tracked separately from local routing.",
            ProviderCategory.SUBSCRIPTION,
            0xFF3B82F6,
            listOf("gpt-5.5", "gpt-5.5-review", "gpt-5.4", "gpt-5.4-mini", "gpt-5.3-codex"),
            "https://chatgpt.com/codex",
            signupUrl = "https://chatgpt.com/codex",
            supportsApiKey = false,
            headers = mapOf(
                "originator" to "codex_cli_rs",
                "User-Agent" to "codex_cli_rs/0.136.0",
            ),
            oauthConfig = OAuthConfig(
                clientId = "app_EMoamEEZ73f0CkXaXp7hrann",
                authorizeUrl = "https://auth.openai.com/oauth/authorize",
                tokenUrl = "https://auth.openai.com/oauth/token",
                scope = "openid profile email offline_access",
                redirectUri = "http://localhost:1455/auth/callback",
                extraAuthParams = mapOf(
                    "id_token_add_organizations" to "true",
                    "codex_cli_simplified_flow" to "true",
                    "originator" to "codex_cli_rs",
                ),
            ),
        ),
        nativeProvider("cursor", "Cursor", ProviderType.BROWSER_LOGIN, "https://api2.cursor.sh", ProviderTransportFormat.CURSOR, AuthScheme.BEARER, ProviderAuthFlow.BROWSER_TOKEN, "Cursor account login metadata for subscription quota routing.", ProviderCategory.SUBSCRIPTION, 0xFF111827, listOf("cursor/gpt-5.4", "cursor/claude-sonnet-4.5", "cursor/gemini-3-pro"), "https://cursor.com", signupUrl = "https://cursor.com", supportsApiKey = false),
        nativeProvider("antigravity", "Google Antigravity", ProviderType.BROWSER_LOGIN, "https://cloudcode-pa.googleapis.com", ProviderTransportFormat.ANTIGRAVITY, AuthScheme.BEARER, ProviderAuthFlow.OAUTH_PKCE, "Antigravity browser OAuth metadata for Google account quota routing.", ProviderCategory.SUBSCRIPTION, 0xFF4285F4, listOf("ag/gemini-3-pro", "ag/claude-sonnet-4.5", "ag/gpt-5.4"), "https://antigravity.google", signupUrl = "https://antigravity.google", supportsApiKey = false),
        openAiProvider(
            "github-copilot",
            "GitHub Copilot",
            "https://api.githubcopilot.com/chat/completions",
            "GitHub Copilot device login. The app exchanges the GitHub device token for a Copilot chat token before runtime use.",
            ProviderCategory.SUBSCRIPTION,
            0xFF2DA44E,
            listOf("gpt-5.2", "gpt-5.2-codex", "gpt-5.3-codex", "gpt-5.4", "gpt-5.4-mini", "claude-sonnet-4.5", "gemini-3.1-pro-preview"),
            signupUrl = "https://github.com/features/copilot",
            headers = mapOf(
                "copilot-integration-id" to "vscode-chat",
                "editor-version" to "vscode/1.110.0",
                "editor-plugin-version" to "copilot-chat/0.38.0",
                "user-agent" to "GitHubCopilotChat/0.38.0",
                "openai-intent" to "conversation-panel",
                "x-github-api-version" to "2025-04-01",
                "x-vscode-user-agent-library-version" to "electron-fetch",
                "X-Initiator" to "user",
            ),
            serviceKinds = listOf("llm", "embedding"),
        ).copy(
            authFlow = ProviderAuthFlow.DEVICE_CODE,
            supportsApiKey = false,
            deviceCodeConfig = DeviceCodeConfig(
                clientId = "Iv1.b507a08c87ecfe98",
                deviceCodeUrl = "https://github.com/login/device/code",
                tokenUrl = "https://github.com/login/oauth/access_token",
                scope = "read:user",
                postExchange = DeviceCodePostExchange.GITHUB_COPILOT_TOKEN,
                postExchangeUrl = "https://api.github.com/copilot_internal/v2/token",
                postExchangeHeaders = mapOf(
                    "Accept" to "application/json",
                    "X-GitHub-Api-Version" to "2022-11-28",
                    "User-Agent" to "GitHubCopilotChat/0.26.7",
                ),
            ),
        ),
        openAiProvider("qwen", "Qwen Code", "https://portal.qwen.ai/v1/chat/completions", "Qwen Code device login using the provider account quota.", ProviderCategory.SUBSCRIPTION, 0xFF7C3AED, listOf("qwen3-coder-plus", "qwen3-coder-flash", "vision-model", "coder-model"), signupUrl = "https://chat.qwen.ai").copy(
            authFlow = ProviderAuthFlow.DEVICE_CODE,
            supportsApiKey = false,
            deviceCodeConfig = DeviceCodeConfig(
                clientId = "f0304373b74a44d2b584a3fb70ca9e56",
                deviceCodeUrl = "https://chat.qwen.ai/api/v1/oauth2/device/code",
                tokenUrl = "https://chat.qwen.ai/api/v1/oauth2/token",
                scope = "openid profile email model.completion",
                codeChallengeMethod = "S256",
            ),
        ),
        openAiProvider("kimi-coding", "Kimi Coding", "https://api.kimi.com/coding/v1/chat/completions", "Kimi Coding device login with direct OpenAI-compatible chat transport.", ProviderCategory.SUBSCRIPTION, 0xFF1E40AF, listOf("kimi-k2.6", "kimi-k2.5", "kimi-k2.5-thinking", "kimi-latest"), signupUrl = "https://kimi.moonshot.cn").copy(
            authFlow = ProviderAuthFlow.DEVICE_CODE,
            supportsApiKey = false,
            deviceCodeConfig = DeviceCodeConfig(
                clientId = "17e5f671-d194-4dfb-9706-5516cb48c098",
                deviceCodeUrl = "https://auth.kimi.com/api/oauth/device_authorization",
                tokenUrl = "https://auth.kimi.com/api/oauth/token",
            ),
        ),
        openAiProvider("iflow", "iFlow AI", "https://apis.iflow.cn/v1/chat/completions", "iFlow browser OAuth connection metadata.", ProviderCategory.SUBSCRIPTION, 0xFF06B6D4, listOf("qwen3-coder-plus", "qwen3-max", "qwen3-vl-plus"), signupUrl = "https://iflow.cn").copy(authFlow = ProviderAuthFlow.OAUTH_PKCE, supportsApiKey = false),
        openAiProvider("kilocode", "Kilo Code", "https://api.kilo.ai/api/openrouter/chat/completions", "Kilo Code device authentication metadata.", ProviderCategory.SUBSCRIPTION, 0xFF6366F1, listOf("anthropic/claude-sonnet-4-20250514", "google/gemini-2.5-pro"), signupUrl = "https://kilocode.ai").copy(authFlow = ProviderAuthFlow.DEVICE_CODE, supportsApiKey = false),
        openAiProvider("cline", "Cline", "https://api.cline.bot/api/v1/chat/completions", "Cline browser-token connection metadata.", ProviderCategory.SUBSCRIPTION, 0xFF8B5CF6, listOf("anthropic/claude-opus-4.7", "anthropic/claude-sonnet-4.6", "openai/gpt-5.3-codex"), signupUrl = "https://cline.bot").copy(authFlow = ProviderAuthFlow.BROWSER_TOKEN, supportsApiKey = false),
        openAiProvider("clinepass", "ClinePass", "https://api.cline.bot/api/v1/chat/completions", "ClinePass supports OAuth and API key modes.", ProviderCategory.SUBSCRIPTION, 0xFFA855F7, listOf("cline-pass/glm-5.2", "cline-pass/kimi-k2.7-code", "cline-pass/deepseek-v4-pro"), signupUrl = "https://app.cline.bot").copy(authFlow = ProviderAuthFlow.BROWSER_TOKEN, supportsApiKey = true),
        openAiProvider("codebuddy-cn", "CodeBuddy CN", "https://copilot.tencent.com/v2/chat/completions", "Tencent CodeBuddy supports browser polling and API-key modes.", ProviderCategory.SUBSCRIPTION, 0xFF0F766E, listOf("glm-5.2", "glm-5.1", "glm-5.0", "glm-5.0-turbo"), signupUrl = "https://copilot.tencent.com").copy(authFlow = ProviderAuthFlow.DEVICE_CODE, supportsApiKey = true),
        openAiProvider("kimchi", "Kimchi", "https://llm.kimchi.dev/openai/v1/chat/completions", "Kimchi browser-token provider.", ProviderCategory.SUBSCRIPTION, 0xFFEC4899, listOf("minimax-m3", "kimi-k2.7", "kimi-k2.6", "kimi-k2.5"), signupUrl = "https://app.kimchi.dev", serviceKinds = listOf("llm", "imageToText")).copy(authFlow = ProviderAuthFlow.BROWSER_TOKEN, supportsApiKey = false),
        nativeProvider("kimi", "Kimi", ProviderType.ANTHROPIC, "https://api.kimi.com/coding/v1/messages", ProviderTransportFormat.CLAUDE_NATIVE, AuthScheme.X_API_KEY, ProviderAuthFlow.API_KEY, "Kimi coding API uses Claude-compatible messages.", ProviderCategory.OFFICIAL, 0xFF111827, listOf("kimi-k2.6", "kimi-k2.5", "kimi-k2.5-thinking", "kimi-latest"), apiKeyUrl = "https://platform.moonshot.ai/console/api-keys", signupUrl = "https://kimi.moonshot.cn", serviceKinds = listOf("llm", "webSearch")),
        nativeProvider("minimax", "Minimax Coding", ProviderType.ANTHROPIC, "https://api.minimax.io/anthropic/v1/messages", ProviderTransportFormat.CLAUDE_NATIVE, AuthScheme.X_API_KEY, ProviderAuthFlow.API_KEY, "MiniMax Claude-compatible coding endpoint.", ProviderCategory.OFFICIAL, 0xFF0EA5E9, listOf("MiniMax-M3", "MiniMax-M2.7", "MiniMax-M2.5"), apiKeyUrl = "https://platform.minimaxi.com/user-center/basic-information/interface-key", signupUrl = "https://www.minimaxi.com", serviceKinds = listOf("llm", "image", "imageToText", "webSearch", "tts")),
        nativeProvider("glm", "GLM Coding", ProviderType.ANTHROPIC, "https://api.z.ai/api/anthropic/v1/messages", ProviderTransportFormat.CLAUDE_NATIVE, AuthScheme.X_API_KEY, ProviderAuthFlow.API_KEY, "GLM Claude-compatible coding endpoint.", ProviderCategory.OFFICIAL, 0xFF1D4ED8, listOf("glm-5.2", "glm-5.1", "glm-5", "glm-4.7"), apiKeyUrl = "https://open.bigmodel.cn/usercenter/apikeys", signupUrl = "https://open.bigmodel.cn"),
        nativeProvider("ollama-cloud", "Ollama Cloud", ProviderType.OPENAI, "https://ollama.com/api/chat", ProviderTransportFormat.OLLAMA, AuthScheme.BEARER, ProviderAuthFlow.API_KEY, "Ollama cloud native chat endpoint.", ProviderCategory.FREE, 0xFF111827, listOf("gpt-oss:120b", "kimi-k2.5", "glm-5", "minimax-m2.5"), apiKeyUrl = "https://ollama.com/settings/keys", signupUrl = "https://ollama.com"),
        CUSTOM_OPENAI,
        CUSTOM_ANTHROPIC,
    )

    fun byId(id: String): ProviderPreset? = all.find { it.id == id }

    fun byProviderType(type: ProviderType): List<ProviderPreset> =
        all.filter { it.providerType == type }
}
