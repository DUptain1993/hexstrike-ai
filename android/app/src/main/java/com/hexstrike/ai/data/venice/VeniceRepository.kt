package com.hexstrike.ai.data.venice

import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

/**
 * Single entry point the rest of the app talks to for Venice AI. Call [configure] whenever the
 * API key / base URL / model settings change (e.g. from a Settings screen); it's cheap to rebuild.
 */
class VeniceRepository(private val factory: VeniceClientFactory = VeniceClientFactory()) {

    @Volatile private var httpClient: OkHttpClient? = null
    @Volatile private var apiService: VeniceApiService? = null
    @Volatile private var streamingClient: VeniceStreamingClient? = null
    @Volatile private var baseUrl: String = VeniceClientFactory.DEFAULT_BASE_URL

    val isConfigured: Boolean get() = apiService != null

    fun configure(apiKey: String, baseUrl: String = VeniceClientFactory.DEFAULT_BASE_URL, verboseLogging: Boolean = false) {
        if (apiKey.isBlank()) {
            httpClient = null
            apiService = null
            streamingClient = null
            return
        }
        val client = factory.buildHttpClient(apiKey, verboseLogging)
        httpClient = client
        apiService = factory.buildApiService(baseUrl, client)
        streamingClient = VeniceStreamingClient(client)
        this.baseUrl = baseUrl
    }

    suspend fun listModels(): Result<List<VeniceModel>> = runCatching {
        requireApiService().listModels().data.filter { it.type == null || it.type == "text" }
    }

    suspend fun chatCompletion(request: ChatCompletionRequest): Result<ChatCompletionResponse> = runCatching {
        requireApiService().chatCompletion(request)
    }

    fun streamChatCompletion(request: ChatCompletionRequest): Flow<StreamEvent> =
        requireStreamingClient().stream(baseUrl, request)

    private fun requireApiService(): VeniceApiService =
        apiService ?: throw IllegalStateException("Add a Venice AI API key in Settings first")

    private fun requireStreamingClient(): VeniceStreamingClient =
        streamingClient ?: throw IllegalStateException("Add a Venice AI API key in Settings first")
}
