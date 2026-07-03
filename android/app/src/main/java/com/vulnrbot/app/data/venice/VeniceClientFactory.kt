package com.vulnrbot.app.data.venice

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val veniceJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    isLenient = true
}

/** Builds a fresh [VeniceApiService] + shared [OkHttpClient] whenever the API key, base URL, or
 * debug-logging preference changes. Cheap enough to recreate on every settings change. */
class VeniceClientFactory {

    fun buildHttpClient(apiKey: String, verboseLogging: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(apiKey))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS) // streaming responses can run indefinitely

        if (verboseLogging) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS },
            )
        }
        return builder.build()
    }

    fun buildApiService(baseUrl: String, httpClient: OkHttpClient): VeniceApiService {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(httpClient)
            .addConverterFactory(veniceJson.asConverterFactory(contentType))
            .build()
            .create(VeniceApiService::class.java)
    }

    private class AuthInterceptor(private val apiKey: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            return chain.proceed(request)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.venice.ai/api/v1/"
    }
}
