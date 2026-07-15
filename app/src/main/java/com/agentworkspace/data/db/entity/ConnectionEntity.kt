package com.agentworkspace.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.agentworkspace.data.model.*

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val providerType: ProviderType,
    val baseUrl: String?,
    val apiKey: String?,
    val isEnabled: Boolean,
    val isHealthy: Boolean,
    val lastHealthCheck: Long?,
    val createdAt: Long,
    val authState: AuthState,
    val presetId: String? = null,
    val authScheme: AuthScheme = AuthScheme.BEARER,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiry: Long? = null,
) {
    fun toDomain(models: List<ModelInfo>): Connection = Connection(
        id = id, name = name, providerType = providerType, baseUrl = baseUrl,
        apiKey = apiKey, isEnabled = isEnabled, isHealthy = isHealthy,
        lastHealthCheck = lastHealthCheck, createdAt = createdAt, models = models,
        authState = authState, presetId = presetId, authScheme = authScheme,
        accessToken = accessToken, refreshToken = refreshToken, tokenExpiry = tokenExpiry,
    )

    fun toDomain(): Connection = toDomain(emptyList())

    companion object {
        fun fromDomain(connection: Connection): ConnectionEntity = ConnectionEntity(
            id = connection.id, name = connection.name,
            providerType = connection.providerType, baseUrl = connection.baseUrl,
            apiKey = connection.apiKey, isEnabled = connection.isEnabled,
            isHealthy = connection.isHealthy, lastHealthCheck = connection.lastHealthCheck,
            createdAt = connection.createdAt, authState = connection.authState,
            presetId = connection.presetId, authScheme = connection.authScheme,
            accessToken = connection.accessToken, refreshToken = connection.refreshToken,
            tokenExpiry = connection.tokenExpiry,
        )
    }
}
