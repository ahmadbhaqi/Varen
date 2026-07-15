package com.agentworkspace.data.security

import com.agentworkspace.data.db.dao.ConnectionDao
import javax.inject.Inject
import javax.inject.Singleton

enum class CredentialField(val marker: String, val storageKey: String) {
    API_KEY("vault:apiKey", "api_key"),
    ACCESS_TOKEN("vault:accessToken", "access_token"),
    REFRESH_TOKEN("vault:refreshToken", "refresh_token"),
}

interface CredentialStore {
    fun put(connectionId: String, field: CredentialField, value: String)
    fun get(connectionId: String, field: CredentialField): String?
    fun removeAll(connectionId: String)
}

data class LegacyCredential(
    val connectionId: String,
    val field: CredentialField,
    val plaintext: String,
)

interface CredentialMigrationSource {
    suspend fun findLegacyCredentials(): List<LegacyCredential>
    suspend fun replaceWithMarker(credential: LegacyCredential)
}

data class CredentialMigrationReport(
    val migrated: Int,
    val failed: Int,
)

@Singleton
class CredentialMigrator @Inject constructor(
    private val credentialStore: CredentialStore,
    private val migrationSource: CredentialMigrationSource,
) {
    suspend fun execute(): CredentialMigrationReport {
        var migrated = 0
        var failed = 0
        migrationSource.findLegacyCredentials().forEach { credential ->
            runCatching {
                credentialStore.put(
                    credential.connectionId,
                    credential.field,
                    credential.plaintext,
                )
                check(
                    credentialStore.get(credential.connectionId, credential.field) ==
                        credential.plaintext,
                ) { "Encrypted credential verification failed" }
                migrationSource.replaceWithMarker(credential)
            }.onSuccess {
                migrated++
            }.onFailure {
                failed++
            }
        }
        return CredentialMigrationReport(migrated = migrated, failed = failed)
    }
}

@Singleton
class RoomCredentialMigrationSource @Inject constructor(
    private val connectionDao: ConnectionDao,
) : CredentialMigrationSource {
    override suspend fun findLegacyCredentials(): List<LegacyCredential> = buildList {
        connectionDao.getAllConnectionsOnce().forEach { connection ->
            addIfLegacy(connection.id, CredentialField.API_KEY, connection.apiKey)
            addIfLegacy(connection.id, CredentialField.ACCESS_TOKEN, connection.accessToken)
            addIfLegacy(connection.id, CredentialField.REFRESH_TOKEN, connection.refreshToken)
        }
    }

    override suspend fun replaceWithMarker(credential: LegacyCredential) {
        val updated = when (credential.field) {
            CredentialField.API_KEY -> connectionDao.replaceApiKey(
                credential.connectionId,
                credential.plaintext,
                credential.field.marker,
            )
            CredentialField.ACCESS_TOKEN -> connectionDao.replaceAccessToken(
                credential.connectionId,
                credential.plaintext,
                credential.field.marker,
            )
            CredentialField.REFRESH_TOKEN -> connectionDao.replaceRefreshToken(
                credential.connectionId,
                credential.plaintext,
                credential.field.marker,
            )
        }
        check(updated == 1) { "Credential changed before migration marker replacement" }
    }

    private fun MutableList<LegacyCredential>.addIfLegacy(
        connectionId: String,
        field: CredentialField,
        value: String?,
    ) {
        val plaintext = value?.takeIf { it.isNotBlank() && it != field.marker } ?: return
        add(LegacyCredential(connectionId, field, plaintext))
    }
}
