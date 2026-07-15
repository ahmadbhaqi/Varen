package com.agentworkspace.data.db.dao

import androidx.room.*
import com.agentworkspace.data.db.entity.ConnectionEntity
import com.agentworkspace.data.db.entity.ModelEntity
import com.agentworkspace.data.model.AuthState
import com.agentworkspace.data.model.AuthScheme
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY createdAt DESC")
    fun getAllConnections(): Flow<List<ConnectionEntity>>
    @Query("SELECT * FROM connections WHERE id = :id")
    fun getConnectionById(id: String): Flow<ConnectionEntity?>
    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun getConnectionByIdOnce(id: String): ConnectionEntity?
    @Query("SELECT * FROM connections WHERE isEnabled = 1")
    fun getEnabledConnections(): Flow<List<ConnectionEntity>>
    @Upsert
    suspend fun upsertConnection(connection: ConnectionEntity)
    @Delete
    suspend fun deleteConnection(connection: ConnectionEntity)
    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun deleteConnectionById(id: String)
    @Query("UPDATE connections SET isHealthy = :healthy, lastHealthCheck = :timestamp WHERE id = :id")
    suspend fun updateHealth(id: String, healthy: Boolean, timestamp: Long = System.currentTimeMillis())
    @Query("UPDATE connections SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
    @Query("UPDATE connections SET authState = :authState WHERE id = :id")
    suspend fun updateAuthState(id: String, authState: AuthState)
    @Query("UPDATE connections SET accessToken = :token, refreshToken = :refresh, tokenExpiry = :expiry, authState = :state WHERE id = :id")
    suspend fun updateTokens(id: String, token: String?, refresh: String?, expiry: Long?, state: AuthState)
    @Query("UPDATE connections SET presetId = :presetId, authScheme = :scheme WHERE id = :id")
    suspend fun updatePreset(id: String, presetId: String?, scheme: AuthScheme)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY name ASC")
    fun getAllModels(): Flow<List<ModelEntity>>
    @Query("SELECT * FROM models WHERE connectionId = :connectionId ORDER BY name ASC")
    fun getModelsForConnection(connectionId: String): Flow<List<ModelEntity>>
    @Query("SELECT * FROM models WHERE connectionId = :connectionId")
    suspend fun getModelsForConnectionOnce(connectionId: String): List<ModelEntity>
    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getModelById(id: String): ModelEntity?
    @Upsert
    suspend fun upsertModel(model: ModelEntity)
    @Upsert
    suspend fun upsertModels(models: List<ModelEntity>)
    @Query("DELETE FROM models WHERE connectionId = :connectionId")
    suspend fun deleteModelsForConnection(connectionId: String)
}
