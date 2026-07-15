package com.agentworkspace.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.agentworkspace.data.db.dao.*
import com.agentworkspace.data.db.entity.*
import com.agentworkspace.data.db.entity.runtime.AgentRunEntity
import com.agentworkspace.data.db.entity.runtime.ApprovalRequestEntity
import com.agentworkspace.data.db.entity.runtime.RunCommandEntity
import com.agentworkspace.data.db.entity.runtime.RunEventEntity
import com.agentworkspace.data.db.entity.runtime.RunMessageEntity
import com.agentworkspace.data.db.entity.runtime.SideEffectEntity

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        ConnectionEntity::class,
        ModelEntity::class,
        UsageRecordEntity::class,
        CheckpointEntity::class,
        DiffEntryEntity::class,
        HistoryEntryEntity::class,
        AgentRunEntity::class,
        RunCommandEntity::class,
        RunEventEntity::class,
        RunMessageEntity::class,
        ApprovalRequestEntity::class,
        SideEffectEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AgentWorkspaceDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun connectionDao(): ConnectionDao
    abstract fun modelDao(): ModelDao
    abstract fun usageDao(): UsageDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun diffDao(): DiffDao
    abstract fun historyDao(): HistoryDao
    abstract fun agentRunDao(): AgentRunDao

    companion object {
        const val DATABASE_NAME = "agent_workspace.db"
    }
}
