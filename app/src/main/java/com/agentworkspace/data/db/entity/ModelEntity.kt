package com.agentworkspace.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.agentworkspace.data.model.AvailabilityState
import com.agentworkspace.data.model.ModelCapabilities
import com.agentworkspace.data.model.ModelInfo

@Entity(
    tableName = "models",
    foreignKeys = [
        ForeignKey(
            entity = ConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("connectionId")],
)
data class ModelEntity(
    @PrimaryKey val id: String,
    val connectionId: String,
    val name: String,
    val capabilities: ModelCapabilities,
    val contextSize: Int?,
    val isRecommended: Boolean,
    val recommendationReason: String?,
    val availabilityState: AvailabilityState,
) {
    fun toDomain(): ModelInfo = ModelInfo(
        id = id,
        name = name,
        connectionId = connectionId,
        capabilities = capabilities,
        contextSize = contextSize,
        isRecommended = isRecommended,
        recommendationReason = recommendationReason,
        availabilityState = availabilityState,
    )

    companion object {
        fun fromDomain(model: ModelInfo): ModelEntity = ModelEntity(
            id = model.id,
            connectionId = model.connectionId,
            name = model.name,
            capabilities = model.capabilities,
            contextSize = model.contextSize,
            isRecommended = model.isRecommended,
            recommendationReason = model.recommendationReason,
            availabilityState = model.availabilityState,
        )
    }
}
