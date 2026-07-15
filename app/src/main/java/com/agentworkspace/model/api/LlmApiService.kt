package com.agentworkspace.model.api

import retrofit2.Response
import retrofit2.http.*

interface LlmApiService {

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatCompletionRequest,
    ): Response<ChatCompletionResponse>

    @Streaming
    @POST("chat/completions")
    suspend fun streamChatCompletion(@Body request: ChatCompletionRequest): Response<okhttp3.ResponseBody>

    @GET("models")
    suspend fun listModels(): Response<ModelsListResponse>
}