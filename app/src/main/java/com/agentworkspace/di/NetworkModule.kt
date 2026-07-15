package com.agentworkspace.di

import com.agentworkspace.model.api.AuthManager
import com.agentworkspace.model.api.LlmApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLlmApiClient(): LlmApiClient = LlmApiClient()

    @Provides
    @Singleton
    fun provideAuthManager(
        @ApplicationContext context: android.content.Context,
        connectionRepository: com.agentworkspace.data.repository.ConnectionRepository,
    ): AuthManager = AuthManager(context, connectionRepository)
}
