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
) : CredentialStore {
    private val prefs = context.getSharedPreferences("agentworkspace_credential_vault", Context.MODE_PRIVATE)

    override fun put(connectionId: String, field: CredentialField, value: String) {
        if (value.isBlank()) {
            remove(connectionId, field)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val persisted = prefs.edit()
            .putString(prefKey(connectionId, field), "${cipher.iv.toBase64()}:${encrypted.toBase64()}")
            .commit()
        check(persisted) { "Unable to persist encrypted credential" }
    }

    override fun get(connectionId: String, field: CredentialField): String? {
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

    override fun removeAll(connectionId: String) {
        prefs.edit()
            .remove(prefKey(connectionId, CredentialField.API_KEY))
            .remove(prefKey(connectionId, CredentialField.ACCESS_TOKEN))
            .remove(prefKey(connectionId, CredentialField.REFRESH_TOKEN))
            .apply()
    }

    private fun remove(connectionId: String, field: CredentialField) {
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

    private fun prefKey(connectionId: String, field: CredentialField): String = "$connectionId:${field.storageKey}"

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "agentworkspace.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
