package com.agentworkspace.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialVault @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("agentworkspace_credential_vault", Context.MODE_PRIVATE)

    fun put(connectionId: String, field: Field, value: String) {
        if (value.isBlank()) {
            remove(connectionId, field)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        prefs.edit()
            .putString(prefKey(connectionId, field), "${cipher.iv.toBase64()}:${encrypted.toBase64()}")
            .apply()
    }

    fun get(connectionId: String, field: Field): String? {
        val stored = prefs.getString(prefKey(connectionId, field), null) ?: return null
        val parts = stored.split(":", limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
            val encrypted = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun removeAll(connectionId: String) {
        prefs.edit()
            .remove(prefKey(connectionId, Field.API_KEY))
            .remove(prefKey(connectionId, Field.ACCESS_TOKEN))
            .remove(prefKey(connectionId, Field.REFRESH_TOKEN))
            .apply()
    }

    private fun remove(connectionId: String, field: Field) {
        prefs.edit().remove(prefKey(connectionId, field)).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun prefKey(connectionId: String, field: Field): String = "$connectionId:${field.storageKey}"

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    enum class Field(val marker: String, val storageKey: String) {
        API_KEY("vault:apiKey", "api_key"),
        ACCESS_TOKEN("vault:accessToken", "access_token"),
        REFRESH_TOKEN("vault:refreshToken", "refresh_token"),
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "agentworkspace.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
