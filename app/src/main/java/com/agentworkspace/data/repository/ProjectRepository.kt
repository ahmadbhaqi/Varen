package com.agentworkspace.data.repository

import com.agentworkspace.data.db.dao.ProjectDao
import com.agentworkspace.data.db.entity.ProjectEntity
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.TrustMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ProjectRepository {
    fun getAllProjects(): Flow<List<Project>>
    fun getProjectById(id: String): Flow<Project?>
    fun getRecentProjects(limit: Int): Flow<List<Project>>
    suspend fun createProject(name: String, path: String, description: String): Project
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(id: String)
    suspend fun updateTrustMode(id: String, trustMode: TrustMode)
    suspend fun updatePreferredModel(id: String, modelId: String?)
    suspend fun updatePreferredConnection(id: String, connectionId: String?)
}

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
) : ProjectRepository {

    override fun getAllProjects(): Flow<List<Project>> =
        projectDao.getAllProjects().map { list -> list.map { it.toDomain() } }

    override fun getProjectById(id: String): Flow<Project?> =
        projectDao.getProjectById(id).map { it?.toDomain() }

    override fun getRecentProjects(limit: Int): Flow<List<Project>> =
        projectDao.getRecentProjects(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun createProject(name: String, path: String, description: String): Project {
        val project = Project(name = name, path = path, description = description)
        projectDao.upsertProject(ProjectEntity.fromDomain(project))
        return project
    }

    override suspend fun updateProject(project: Project) {
        projectDao.upsertProject(ProjectEntity.fromDomain(project.copy(updatedAt = System.currentTimeMillis())))
    }

    override suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
    }

    override suspend fun updateTrustMode(id: String, trustMode: TrustMode) {
        projectDao.updateTrustMode(id, trustMode)
    }

    override suspend fun updatePreferredModel(id: String, modelId: String?) {
        projectDao.updatePreferredModel(id, modelId)
    }

    override suspend fun updatePreferredConnection(id: String, connectionId: String?) {
        projectDao.updatePreferredConnection(id, connectionId)
    }
}
