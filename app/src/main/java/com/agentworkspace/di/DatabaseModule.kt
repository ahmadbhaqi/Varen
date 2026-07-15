package com.agentworkspace.di

import android.content.Context
import androidx.room.Room
import com.agentworkspace.data.db.AgentWorkspaceDatabase
import com.agentworkspace.data.db.dao.*
import com.agentworkspace.data.db.migration.MIGRATION_2_3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AgentWorkspaceDatabase =
        Room.databaseBuilder(
            context,
            AgentWorkspaceDatabase::class.java,
            AgentWorkspaceDatabase.DATABASE_NAME,
        )
            .addMigrations(MIGRATION_2_3)
            .build()

    @Provides
    fun provideProjectDao(db: AgentWorkspaceDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideTaskDao(db: AgentWorkspaceDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideConnectionDao(db: AgentWorkspaceDatabase): ConnectionDao = db.connectionDao()

    @Provides
    fun provideModelDao(db: AgentWorkspaceDatabase): ModelDao = db.modelDao()

    @Provides
    fun provideUsageDao(db: AgentWorkspaceDatabase): UsageDao = db.usageDao()

    @Provides
    fun provideCheckpointDao(db: AgentWorkspaceDatabase): CheckpointDao = db.checkpointDao()

    @Provides
    fun provideDiffDao(db: AgentWorkspaceDatabase): DiffDao = db.diffDao()

    @Provides
    fun provideHistoryDao(db: AgentWorkspaceDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideAgentRunDao(db: AgentWorkspaceDatabase): AgentRunDao = db.agentRunDao()
}
