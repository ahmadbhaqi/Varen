package com.agentworkspace.data.db.dao

import androidx.room.*
import com.agentworkspace.data.db.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectByIdOnce(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentProjects(limit: Int = 10): Flow<List<ProjectEntity>>

    @Upsert
    suspend fun upsertProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("UPDATE projects SET updatedAt = :timestamp WHERE id = :id")
    suspend fun touchProject(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE projects SET trustMode = :trustMode WHERE id = :id")
    suspend fun updateTrustMode(id: String, trustMode: com.agentworkspace.data.model.TrustMode)

    @Query("UPDATE projects SET preferredModelId = :modelId WHERE id = :id")
    suspend fun updatePreferredModel(id: String, modelId: String?)

    @Query("UPDATE projects SET preferredConnectionId = :connectionId WHERE id = :id")
    suspend fun updatePreferredConnection(id: String, connectionId: String?)
}
