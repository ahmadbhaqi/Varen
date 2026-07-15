package com.agentworkspace.di

import com.agentworkspace.data.repository.*
import com.agentworkspace.runtime.data.AgentRunRepository
import com.agentworkspace.runtime.data.ApprovalStore
import com.agentworkspace.runtime.data.RoomAgentRunRepository
import com.agentworkspace.runtime.data.RunCreator
import com.agentworkspace.runtime.data.RunLeaseStore
import com.agentworkspace.runtime.data.RunTimelineStore
import com.agentworkspace.runtime.data.RunProgressStore
import com.agentworkspace.runtime.data.RunRecoveryStore
import com.agentworkspace.runtime.application.RepositoryRunStartDataSource
import com.agentworkspace.runtime.application.RunStartDataSource
import com.agentworkspace.runtime.application.ApprovalRequester
import com.agentworkspace.runtime.application.RunApprovalCoordinator
import com.agentworkspace.runtime.compat.AndroidLegacyRuntimeGateway
import com.agentworkspace.runtime.compat.LegacyRunContextLoader
import com.agentworkspace.runtime.compat.LegacyRunExecutor
import com.agentworkspace.runtime.compat.LegacyRuntimeGateway
import com.agentworkspace.runtime.compat.RepositoryLegacyRunContextLoader
import com.agentworkspace.runtime.domain.IdGenerator
import com.agentworkspace.runtime.domain.RuntimeClock
import com.agentworkspace.runtime.domain.SystemRuntimeClock
import com.agentworkspace.runtime.domain.UuidGenerator
import com.agentworkspace.runtime.service.RunLauncher
import com.agentworkspace.runtime.service.RunExecutor
import com.agentworkspace.runtime.service.RunLeaseController
import com.agentworkspace.runtime.service.RunLeaseManager
import com.agentworkspace.runtime.service.RunServiceController
import com.agentworkspace.readiness.application.AndroidWorkspaceAccessChecker
import com.agentworkspace.readiness.domain.WorkspaceAccessChecker
import com.agentworkspace.data.security.CredentialMigrationSource
import com.agentworkspace.data.security.CredentialStore
import com.agentworkspace.data.security.CredentialVault
import com.agentworkspace.data.security.RoomCredentialMigrationSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(impl: ConnectionRepositoryImpl): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository

    @Binds
    @Singleton
    abstract fun bindCheckpointRepository(impl: CheckpointRepositoryImpl): CheckpointRepository

    @Binds
    @Singleton
    abstract fun bindDiffRepository(impl: DiffRepositoryImpl): DiffRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindAgentRunRepository(impl: RoomAgentRunRepository): AgentRunRepository

    @Binds
    @Singleton
    abstract fun bindApprovalStore(impl: RoomAgentRunRepository): ApprovalStore

    @Binds
    @Singleton
    abstract fun bindRunLeaseStore(impl: RoomAgentRunRepository): RunLeaseStore

    @Binds
    @Singleton
    abstract fun bindRunTimelineStore(impl: RoomAgentRunRepository): RunTimelineStore

    @Binds
    @Singleton
    abstract fun bindRunProgressStore(impl: RoomAgentRunRepository): RunProgressStore

    @Binds
    @Singleton
    abstract fun bindRunRecoveryStore(impl: RoomAgentRunRepository): RunRecoveryStore

    @Binds
    @Singleton
    abstract fun bindRunCreator(impl: RoomAgentRunRepository): RunCreator

    @Binds
    @Singleton
    abstract fun bindRunStartDataSource(impl: RepositoryRunStartDataSource): RunStartDataSource

    @Binds
    @Singleton
    abstract fun bindLegacyRunContextLoader(impl: RepositoryLegacyRunContextLoader): LegacyRunContextLoader

    @Binds
    @Singleton
    abstract fun bindLegacyRuntimeGateway(impl: AndroidLegacyRuntimeGateway): LegacyRuntimeGateway

    @Binds
    @Singleton
    abstract fun bindApprovalRequester(impl: RunApprovalCoordinator): ApprovalRequester

    @Binds
    @Singleton
    abstract fun bindRunLauncher(impl: RunServiceController): RunLauncher

    @Binds
    @Singleton
    abstract fun bindRunLeaseController(impl: RunLeaseManager): RunLeaseController

    @Binds
    @Singleton
    abstract fun bindRunExecutor(impl: LegacyRunExecutor): RunExecutor

    @Binds
    @Singleton
    abstract fun bindRuntimeClock(impl: SystemRuntimeClock): RuntimeClock

    @Binds
    @Singleton
    abstract fun bindIdGenerator(impl: UuidGenerator): IdGenerator

    @Binds
    @Singleton
    abstract fun bindWorkspaceAccessChecker(impl: AndroidWorkspaceAccessChecker): WorkspaceAccessChecker

    @Binds
    @Singleton
    abstract fun bindCredentialStore(impl: CredentialVault): CredentialStore

    @Binds
    @Singleton
    abstract fun bindCredentialMigrationSource(
        impl: RoomCredentialMigrationSource,
    ): CredentialMigrationSource
}
