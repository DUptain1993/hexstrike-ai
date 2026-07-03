package com.vulnrbot.app.data.venice

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

sealed interface StreamEvent {
    data class Chunk(val choice: ChatChoice) : StreamEvent
    data class Failed(val error: Throwable) : StreamEvent
    data object Completed : StreamEvent
}

/**
 * Streams `POST chat/completions` with `stream: true` and hand-parses the
 * `text/event-stream` body (`data: {json}` lines, terminated by `data: [DONE]`)
 * since this shape doesn't fit Retrofit's request/response model.
 */
class VeniceStreamingClient(private val httpClient: OkHttpClient) {

    fun stream(baseUrl: String, request: ChatCompletionRequest): Flow<StreamEvent> = callbackFlow {
        val url = (if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/") + "chat/completions"
        val body = veniceJson.encodeToString(request.copy(stream = true))
            .toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder().url(url).post(body).build()

        val call = httpClient.newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(StreamEvent.Failed(e))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string().orEmpty()
                        trySend(StreamEvent.Failed(VeniceApiException(resp.code, errorBody.ifBlank { resp.message })))
                        close()
                        return
                    }
                    val source = resp.body?.source()
                    try {
                        while (source != null && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload == "[DONE]") {
                                trySend(StreamEvent.Completed)
                                break
                            }
                            if (payload.isEmpty()) continue
                            runCatching { veniceJson.decodeFromString<ChatCompletionResponse>(payload) }
                                .onSuccess { parsed ->
                                    parsed.choices.forEach { trySend(StreamEvent.Chunk(it)) }
                                }
                        }
                        trySend(StreamEvent.Completed)
                    } catch (e: IOException) {
                        trySend(StreamEvent.Failed(e))
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose { call.cancel() }
    }
}
