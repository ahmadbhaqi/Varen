package com.agentworkspace.data.repository

import com.agentworkspace.data.db.dao.ConnectionDao
import com.agentworkspace.data.db.dao.ModelDao
import com.agentworkspace.data.db.entity.ConnectionEntity
import com.agentworkspace.data.db.entity.ModelEntity
import com.agentworkspace.data.model.*
import com.agentworkspace.data.security.CredentialVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ConnectionRepository {
    fun getAllConnections(): Flow<List<Connection>>
    fun getConnectionById(id: String): Flow<Connection?>
    fun getEnabledConnections(): Flow<List<Connection>>
    suspend fun createConnection(
        name: String,
        providerType: ProviderType,
        baseUrl: String?,
        apiKey: String?,
    ): Connection
    suspend fun updateConnection(connection: Connection)
    suspend fun deleteConnection(id: String)
    suspend fun updateHealth(id: String, healthy: Boolean)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun updateConnectionAuth(
        id: String,
        apiKey: String? = null,
        accessToken: String? = null,
        refreshToken: String? = null,
        tokenExpiry: Long? = null,
        authState: AuthState,
    )
    suspend fun updatePreset(id: String, presetId: String?, authScheme: AuthScheme)
    suspend fun updateAuthState(id: String, authState: AuthState)
    suspend fun syncModels(connectionId: String, models: List<ModelInfo>)
    suspend fun upsertModel(model: ModelInfo)
    fun getAllModels(): Flow<List<ModelInfo>>
    fun getModelsForConnection(connectionId: String): Flow<List<ModelInfo>>
    suspend fun getModelById(id: String): ModelInfo?
}

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val connectionDao: ConnectionDao,
    private val modelDao: ModelDao,
    private val credentialVault: CredentialVault,
) : ConnectionRepository {

    override fun getAllConnections(): Flow<List<Connection>> =
        combine(
            connectionDao.getAllConnections(),
            modelDao.getAllModels(),
        ) { connections, models ->
            val modelsByConnection = models.groupBy { it.connectionId }
            connections.map { conn ->
                val connModels = modelsByConnection[conn.id]?.map { it.toDomain() } ?: emptyList()
                conn.toDomain(connModels).hydrateCredentials()
            }
        }

    override fun getConnectionById(id: String): Flow<Connection?> =
        connectionDao.getConnectionById(id).map { it?.toDomain()?.hydrateCredentials() }

    override fun getEnabledConnections(): Flow<List<Connection>> =
        connectionDao.getEnabledConnections().map { list -> list.map { it.toDomain().hydrateCredentials() } }

    override suspend fun createConnection(
        name: String,
        providerType: ProviderType,
        baseUrl: String?,
        apiKey: String?,
    ): Connection {
        val connection = Connection(
            name = name,
            providerType = providerType,
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
        connectionDao.upsertConnection(ConnectionEntity.fromDomain(connection.secureForStorage()))
        return connection
    }

    override suspend fun updateConnection(connection: Connection) {
        connectionDao.upsertConnection(ConnectionEntity.fromDomain(connection.secureForStorage()))
    }

    override suspend fun deleteConnection(id: String) {
        credentialVault.removeAll(id)
        connectionDao.deleteConnectionById(id)
    }

    override suspend fun updateHealth(id: String, healthy: Boolean) {
        connectionDao.updateHealth(id, healthy)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        connectionDao.setEnabled(id, enabled)
    }

    override suspend fun updateConnectionAuth(
        id: String,
        apiKey: String?,
        accessToken: String?,
        refreshToken: String?,
        tokenExpiry: Long?,
        authState: AuthState,
    ) {
        val existing = connectionDao.getConnectionByIdOnce(id)
        if (existing != null) {
            val storageConnection = existing.toDomain().copy(
                apiKey = apiKey ?: existing.apiKey,
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenExpiry = tokenExpiry,
                authState = authState,
            ).secureForStorage()
            connectionDao.upsertConnection(existing.copy(
                apiKey = storageConnection.apiKey,
                accessToken = storageConnection.accessToken,
                refreshToken = storageConnection.refreshToken,
                tokenExpiry = tokenExpiry,
                authState = authState,
            ))
        }
    }

    override suspend fun updatePreset(id: String, presetId: String?, authScheme: AuthScheme) {
        connectionDao.updatePreset(id, presetId, authScheme)
    }
    override suspend fun updateAuthState(id: String, authState: AuthState) {
        connectionDao.updateAuthState(id, authState)
    }

    override suspend fun syncModels(connectionId: String, models: List<ModelInfo>) {
        modelDao.deleteModelsForConnection(connectionId)
        modelDao.upsertModels(models.map { ModelEntity.fromDomain(it) })
    }

    override suspend fun upsertModel(model: ModelInfo) {
        modelDao.upsertModel(ModelEntity.fromDomain(model))
    }

    override fun getAllModels(): Flow<List<ModelInfo>> =
        modelDao.getAllModels().map { list -> list.map { it.toDomain() } }

    override fun getModelsForConnection(connectionId: String): Flow<List<ModelInfo>> =
        modelDao.getModelsForConnection(connectionId).map { list -> list.map { it.toDomain() } }

    override suspend fun getModelById(id: String): ModelInfo? =
        modelDao.getModelById(id)?.toDomain()

    private fun Connection.hydrateCredentials(): Connection = copy(
        apiKey = resolveCredential(id, apiKey, CredentialVault.Field.API_KEY),
        accessToken = resolveCredential(id, accessToken, CredentialVault.Field.ACCESS_TOKEN),
        refreshToken = resolveCredential(id, refreshToken, CredentialVault.Field.REFRESH_TOKEN),
    )

    private fun Connection.secureForStorage(): Connection = copy(
        apiKey = secureCredential(id, apiKey, CredentialVault.Field.API_KEY),
        accessToken = secureCredential(id, accessToken, CredentialVault.Field.ACCESS_TOKEN),
        refreshToken = secureCredential(id, refreshToken, CredentialVault.Field.REFRESH_TOKEN),
    )

    private fun resolveCredential(connectionId: String, stored: String?, field: CredentialVault.Field): String? =
        if (stored == field.marker) credentialVault.get(connectionId, field) else stored

    private fun secureCredential(connectionId: String, value: String?, field: CredentialVault.Field): String? {
        if (value == null) return null
        if (value == field.marker) return value
        credentialVault.put(connectionId, field, value)
        return field.marker
    }
}
