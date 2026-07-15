package com.agentworkspace.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.agentworkspace.data.model.Project
import com.agentworkspace.data.model.TrustMode

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trustMode: TrustMode,
    val preferredModelId: String?,
    val preferredConnectionId: String?,
    val isActive: Boolean,
    val description: String,
) {
    fun toDomain(): Project = Project(
        id = id,
        name = name,
        path = path,
        createdAt = createdAt,
        updatedAt = updatedAt,
        trustMode = trustMode,
        preferredModelId = preferredModelId,
        preferredConnectionId = preferredConnectionId,
        isActive = isActive,
        description = description,
    )

    companion object {
        fun fromDomain(project: Project): ProjectEntity = ProjectEntity(
            id = project.id,
            name = project.name,
            path = project.path,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            trustMode = project.trustMode,
            preferredModelId = project.preferredModelId,
            preferredConnectionId = project.preferredConnectionId,
            isActive = project.isActive,
            description = project.description,
        )
    }
}
