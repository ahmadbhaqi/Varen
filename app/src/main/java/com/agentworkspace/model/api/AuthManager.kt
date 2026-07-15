package com.agentworkspace.model.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.DeviceCodeConfig
import com.agentworkspace.data.model.DeviceCodePostExchange
import com.agentworkspace.data.model.OAuthConfig
import com.agentworkspace.data.model.ProviderPreset
import com.agentworkspace.data.model.TokenExchangeEncoding
import com.agentworkspace.data.repository.ConnectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionRepository: ConnectionRepository,
) {
    companion object {
        private const val TAG = "AuthManager"
        const val REDIRECT_URI = "agentworkspace://auth"
        private const val PREFS = "agentworkspace_oauth"
        private const val KEY_CONNECTION_ID = "connection_id"
        private const val KEY_PRESET_ID = "preset_id"
        private const val KEY_STATE = "state"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_REDIRECT_URI = "redirect_uri"
        private const val OAUTH_TIMEOUT_MS = 5L * 60 * 1000L
        private const val AUTH_SUCCESS_HTML = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Authorized</title><style>body{font-family:-apple-system,system-ui,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;background:#0f1117;color:#e6e6e6}.c{text-align:center}.h{font-size:22px;font-weight:600;margin:16px 0 6px}.p{opacity:.7}.d{margin-top:20px;font-size:13px;opacity:.5}</style></head><body><div class='c'><div class='h'>Authorization complete</div><div class='p'>You can close this tab and return to the app.</div><div class='d'>Agent Workspace</div></div></body></html>"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val client = OkHttpClient.Builder().build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopbackServer: ServerSocket? = null
    private var loopbackJob: Job? = null

    data class DeviceCodeSession(
        val connectionId: String,
        val presetId: String,
        val providerName: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String?,
        val intervalSeconds: Int,
        val expiresInSeconds: Int,
        val deviceCode: String,
        val codeVerifier: String?,
    )

    fun launchProviderPage(preset: ProviderPreset) {
        launchUrl(buildFallbackUrl(preset))
    }

    fun launchExternalUrl(url: String) {
        launchUrl(url)
    }

    suspend fun beginOAuthLogin(preset: ProviderPreset, connectionId: String): Boolean = withContext(Dispatchers.IO) {
        val oauth = preset.oauthConfig ?: return@withContext false
        val verifier = randomUrlToken(48)
        val challenge = codeChallenge(verifier)
        val state = randomUrlToken(24)
        val redirectUri = oauth.redirectUri ?: REDIRECT_URI

        prefs.edit()
            .putString(KEY_CONNECTION_ID, connectionId)
            .putString(KEY_PRESET_ID, preset.id)
            .putString(KEY_STATE, state)
            .putString(KEY_VERIFIER, verifier)
            .putString(KEY_REDIRECT_URI, redirectUri)
            .apply()

        if (isLoopbackRedirect(redirectUri)) {
            val port = loopbackPort(redirectUri)
            if (port == null || !startLoopbackCapture(port)) {
                Log.w(TAG, "Could not bind loopback callback for " + redirectUri)
                return@withContext false
            }
        }

        val authUrl = buildAuthUrl(oauth, redirectUri, state, challenge)
        launchUrl(authUrl)
        true
    }

    suspend fun beginDeviceCodeLogin(preset: ProviderPreset, connectionId: String): DeviceCodeSession? =
        withContext(Dispatchers.IO) {
            val config = preset.deviceCodeConfig ?: return@withContext null
            val verifier = if (config.codeChallengeMethod != null) randomUrlToken(48) else null
            val challenge = verifier?.let { codeChallenge(it) }
            val response = requestDeviceCode(config, challenge)
            val session = DeviceCodeSession(
                connectionId = connectionId,
                presetId = preset.id,
                providerName = preset.displayName,
                userCode = response.string("user_code").orEmpty(),
                verificationUri = response.string("verification_uri")
                    ?: response.string("verification_url")
                    ?: response.string("authUrl")
                    ?: buildFallbackUrl(preset),
                verificationUriComplete = response.string("verification_uri_complete"),
                intervalSeconds = response.int("interval")?.coerceAtLeast(1) ?: 5,
                expiresInSeconds = response.int("expires_in") ?: 600,
                deviceCode = response.string("device_code")
                    ?: response.string("code")
                    ?: throw IllegalStateException("Device authorization response did not include a device code"),
                codeVerifier = verifier,
            )
            launchUrl(session.verificationUriComplete ?: session.verificationUri)
            session
        }

    suspend fun completeDeviceCodeLogin(session: DeviceCodeSession): Boolean = withContext(Dispatchers.IO) {
        val preset = com.agentworkspace.data.model.ProviderPresets.byId(session.presetId) ?: return@withContext false
        val config = preset.deviceCodeConfig ?: return@withContext false
        val deadline = System.currentTimeMillis() + session.expiresInSeconds * 1000L
        var intervalMs = session.intervalSeconds * 1000L

        while (System.currentTimeMillis() < deadline) {
            val tokenResult = runCatching { pollDeviceToken(config, session.deviceCode, session.codeVerifier) }
            val tokenJson = tokenResult.getOrNull()
            if (tokenResult.isFailure || tokenJson == null) {
                Log.w(TAG, "Device token polling failed for ${session.presetId}", tokenResult.exceptionOrNull())
                delay(intervalMs)
                continue
            }

            val accessToken = tokenJson.string("access_token")
            if (!accessToken.isNullOrBlank()) {
                return@withContext runCatching {
                    saveDeviceTokens(config, session.connectionId, tokenJson)
                }.onFailure { err ->
                    Log.w(TAG, "Device token save failed for ${session.presetId}", err)
                    connectionRepository.updateAuthState(session.connectionId, AuthState.ERROR)
                }.isSuccess
            }

            when (tokenJson.string("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> intervalMs += 5000L
                "expired_token" -> {
                    connectionRepository.updateAuthState(session.connectionId, AuthState.EXPIRED)
                    return@withContext false
                }
                else -> {
                    Log.w(TAG, "Device token polling returned no access token for ${session.presetId}: $tokenJson")
                    connectionRepository.updateAuthState(session.connectionId, AuthState.ERROR)
                    return@withContext false
                }
            }
            delay(intervalMs)
        }

        connectionRepository.updateAuthState(session.connectionId, AuthState.EXPIRED)
        false
    }

    suspend fun handleAuthCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (uri.scheme != "agentworkspace" || uri.host != "auth") return@withContext false
        stopLoopbackServer()
        completeOAuthExchange(
            code = uri.getQueryParameter("code"),
            returnedState = uri.getQueryParameter("state"),
        )
    }

    private suspend fun completeOAuthExchange(code: String?, returnedState: String?): Boolean = withContext(Dispatchers.IO) {
        val connectionId = prefs.getString(KEY_CONNECTION_ID, null)
        val presetId = prefs.getString(KEY_PRESET_ID, null)
        val expectedState = prefs.getString(KEY_STATE, null)
        val verifier = prefs.getString(KEY_VERIFIER, null)
        val redirectUri = prefs.getString(KEY_REDIRECT_URI, REDIRECT_URI) ?: REDIRECT_URI
        if (connectionId == null || presetId == null || expectedState == null || verifier == null) {
            return@withContext false
        }
        val preset = com.agentworkspace.data.model.ProviderPresets.byId(presetId)
        if (preset == null) {
            connectionRepository.updateAuthState(connectionId, AuthState.ERROR)
            return@withContext false
        }
        val oauth = preset.oauthConfig
        if (oauth == null) {
            connectionRepository.updateAuthState(connectionId, AuthState.ERROR)
            return@withContext false
        }

        if (returnedState != null && returnedState != expectedState) {
            Log.w(TAG, "OAuth state mismatch for " + presetId)
            connectionRepository.updateAuthState(connectionId, AuthState.ERROR)
            return@withContext false
        }
        if (code.isNullOrBlank()) {
            Log.w(TAG, "OAuth callback missing code for " + presetId)
            connectionRepository.updateAuthState(connectionId, AuthState.ERROR)
            return@withContext false
        }

        runCatching {
            val tokenJson = exchangeToken(oauth, code, redirectUri, verifier, expectedState)
            val accessToken = tokenJson.string("access_token")
            val refreshToken = tokenJson.string("refresh_token")
            val expiresIn = tokenJson.int("expires_in")
            if (accessToken.isNullOrBlank()) {
                throw IllegalStateException("OAuth response did not include an access token")
            }
            val expiry = expiresIn?.let { System.currentTimeMillis() + it * 1000L }
            connectionRepository.updateConnectionAuth(
                id = connectionId,
                apiKey = null,
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenExpiry = expiry,
                authState = AuthState.AUTHENTICATED,
            )
            prefs.edit().clear().apply()
        }.onFailure { err ->
            Log.w(TAG, "OAuth exchange failed for " + presetId, err)
            connectionRepository.updateAuthState(connectionId, AuthState.ERROR)
        }.isSuccess
    }

    private fun isLoopbackRedirect(uri: String): Boolean =
        uri.startsWith("http://localhost") || uri.startsWith("http://127.0.0.1")

    private fun loopbackPort(uri: String): Int? = runCatching {
        val tail = uri.removePrefix("http://localhost").removePrefix("http://127.0.0.1")
        if (!tail.startsWith(":")) return@runCatching null
        tail.removePrefix(":").substringBefore('/').toIntOrNull()
    }.getOrNull()

    private fun startLoopbackCapture(port: Int): Boolean {
        stopLoopbackServer()
        val server = try {
            ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            Log.w(TAG, "Loopback server bind failed on port " + port, e)
            return false
        }
        loopbackServer = server
        loopbackJob = applicationScope.launch {
            try {
                val socket = withTimeoutOrNull(OAUTH_TIMEOUT_MS) {
                    runInterruptible(Dispatchers.IO) { server.accept() }
                }
                if (socket == null) {
                    Log.w(TAG, "Loopback OAuth capture timed out")
                    markCurrentConnectionError()
                    return@launch
                }
                try {
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine() ?: ""
                    val captured = parseCallbackQuery(requestLine)
                    val payload = AUTH_SUCCESS_HTML.toByteArray(Charsets.UTF_8)
                    val header = ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + payload.size + "\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
                    val out: OutputStream = socket.getOutputStream()
                    out.write(header)
                    out.write(payload)
                    out.flush()
                    completeOAuthExchange(captured["code"], captured["state"])
                } finally {
                    runCatching { socket.close() }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Loopback OAuth capture failed", e)
                markCurrentConnectionError()
            } finally {
                runCatching { server.close() }
                if (loopbackServer === server) loopbackServer = null
            }
        }
        return true
    }

    private fun stopLoopbackServer() {
        loopbackJob?.cancel()
        loopbackJob = null
        runCatching { loopbackServer?.close() }
        loopbackServer = null
    }

    private suspend fun markCurrentConnectionError() {
        val connectionId = prefs.getString(KEY_CONNECTION_ID, null) ?: return
        connectionRepository.updateAuthState(connectionId, AuthState.ERROR)
    }

    private fun parseCallbackQuery(requestLine: String): Map<String, String> {
        val raw = requestLine.substringAfter(' ').substringBefore(" HTTP").trim()
        val query = raw.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        query.split('&').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx >= 0) {
                result[Uri.decode(pair.substring(0, idx))] = Uri.decode(pair.substring(idx + 1))
            }
        }
        return result
    }

    private fun launchUrl(url: String) {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, Uri.parse(url))
    }

    private fun buildFallbackUrl(preset: ProviderPreset): String =
        preset.browserLoginUrl ?: preset.apiKeyUrl ?: preset.signupUrl ?: preset.baseUrl

    private fun requestDeviceCode(config: DeviceCodeConfig, codeChallenge: String?): JsonObject {
        val form = FormBody.Builder()
            .add("client_id", config.clientId)
        val scopeValue = config.scope ?: config.scopes.joinToString(" ")
        if (scopeValue.isNotBlank()) form.add("scope", scopeValue)
        if (codeChallenge != null) {
            form.add("code_challenge", codeChallenge)
            form.add("code_challenge_method", config.codeChallengeMethod ?: "S256")
        }
        config.extraDeviceParams.forEach { (key, value) -> form.add(key, value) }

        val request = Request.Builder()
            .url(config.deviceCodeUrl)
            .post(form.build())
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Device code request failed (${response.code}): ${body.take(300)}")
            }
            return json.parseToJsonElement(body) as? JsonObject
                ?: throw IllegalStateException("Device code request returned a non-object response")
        }
    }

    private fun pollDeviceToken(config: DeviceCodeConfig, deviceCode: String, codeVerifier: String?): JsonObject {
        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("client_id", config.clientId)
            .add("device_code", deviceCode)
        codeVerifier?.let { form.add("code_verifier", it) }
        config.extraTokenParams.forEach { (key, value) -> form.add(key, value) }

        val request = Request.Builder()
            .url(config.tokenUrl)
            .post(form.build())
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: buildJsonObject {
                    put("error", if (response.isSuccessful) "invalid_response" else "http_${response.code}")
                    put("error_description", body.take(300))
                }
        }
    }

    private suspend fun saveDeviceTokens(config: DeviceCodeConfig, connectionId: String, tokenJson: JsonObject) {
        val rawAccessToken = tokenJson.string("access_token")
            ?: throw IllegalStateException("Device token response did not include an access token")
        val refreshToken = tokenJson.string("refresh_token")
        val expiresIn = tokenJson.int("expires_in")
        val defaultExpiry = expiresIn?.let { System.currentTimeMillis() + it * 1000L }
        val (runtimeAccessToken, runtimeExpiry) = when (config.postExchange) {
            DeviceCodePostExchange.NONE -> rawAccessToken to defaultExpiry
            DeviceCodePostExchange.GITHUB_COPILOT_TOKEN -> exchangeGithubCopilotToken(rawAccessToken, config, defaultExpiry)
        }
        connectionRepository.updateConnectionAuth(
            id = connectionId,
            apiKey = null,
            accessToken = runtimeAccessToken,
            refreshToken = refreshToken,
            tokenExpiry = runtimeExpiry,
            authState = AuthState.AUTHENTICATED,
        )
    }

    private fun exchangeGithubCopilotToken(
        githubAccessToken: String,
        config: DeviceCodeConfig,
        fallbackExpiry: Long?,
    ): Pair<String, Long?> {
        val url = config.postExchangeUrl
            ?: throw IllegalStateException("GitHub Copilot post-exchange URL is not configured")
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $githubAccessToken")
        config.postExchangeHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Copilot token exchange failed (${response.code}): ${body.take(300)}")
            }
            val root = json.parseToJsonElement(body) as? JsonObject
                ?: throw IllegalStateException("Copilot token exchange returned a non-object response")
            val token = root.string("token")
                ?: throw IllegalStateException("Copilot token exchange returned no token")
            val expiresAt = root.string("expires_at")?.let { raw ->
                runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            }
            return token to (expiresAt ?: fallbackExpiry)
        }
    }

    private fun buildAuthUrl(oauth: OAuthConfig, redirectUri: String, state: String, challenge: String): String {
        val builder = Uri.parse(oauth.authorizeUrl).buildUpon()
            .appendQueryParameter("client_id", oauth.clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", oauth.codeChallengeMethod)
            .appendQueryParameter("state", state)

        val scopeValue = oauth.scope ?: oauth.scopes.joinToString(" ")
        if (scopeValue.isNotBlank()) builder.appendQueryParameter("scope", scopeValue)
        oauth.extraAuthParams.forEach { (key, value) -> builder.appendQueryParameter(key, value) }

        return builder.build().toString()
    }

    private fun exchangeToken(
        oauth: OAuthConfig,
        code: String,
        redirectUri: String,
        verifier: String,
        state: String,
    ): JsonObject {
        val requestBody = when (oauth.tokenExchangeEncoding) {
            TokenExchangeEncoding.FORM_URLENCODED -> FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", oauth.clientId)
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", verifier)
                .build()
            TokenExchangeEncoding.JSON -> buildJsonObject {
                put("grant_type", "authorization_code")
                put("client_id", oauth.clientId)
                put("code", code.substringBefore("#"))
                put("state", code.substringAfter("#", state))
                put("redirect_uri", redirectUri)
                put("code_verifier", verifier)
            }.toString().toRequestBody("application/json".toMediaType())
        }

        val request = Request.Builder()
            .url(oauth.tokenUrl)
            .post(requestBody)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Token exchange failed (${response.code}): ${body.take(300)}")
            }
            return json.parseToJsonElement(body) as? JsonObject
                ?: throw IllegalStateException("Token exchange returned a non-object response")
        }
    }

    private fun randomUrlToken(bytes: Int): String {
        val data = ByteArray(bytes)
        random.nextBytes(data)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull
}
