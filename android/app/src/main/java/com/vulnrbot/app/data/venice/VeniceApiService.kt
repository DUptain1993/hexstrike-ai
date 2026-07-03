package com.vulnrbot.app.data.venice

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface VeniceApiService {
    @GET("models")
    suspend fun listModels(@Query("type") type: String = "text"): ModelsResponse

    @POST("chat/completions")
    suspend fun chatCompletion(@Body request: ChatCompletionRequest): ChatCompletionResponse
}
