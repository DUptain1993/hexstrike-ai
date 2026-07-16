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
 *
 * Venice doesn't always send SSE even when we ask for it: some models ignore `stream: true`
 * and return a single non-streamed `{"choices":[{"message":...}]}` JSON body, and a rejected
 * request can come back as HTTP 200 with a plain `{"error":...}` JSON body. Both cases used to
 * be swallowed silently (no `data:` lines → nothing emitted → the UI showed no reply and no
 * error), so this parser now treats a non-SSE body as a real response/error instead of dropping
 * it, and surfaces decode failures rather than ignoring them.
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
                    var sawSseData = false
                    var emittedChunk = false
                    var lastDecodeError: Throwable? = null
                    val rawBody = StringBuilder()
                    try {
                        while (source != null && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) {
                                // Buffer non-SSE lines (capped) so a non-streamed JSON body or a
                                // plain JSON error can be recovered after the loop.
                                if (rawBody.length < MAX_RAW_BODY) rawBody.append(line).append('\n')
                                continue
                            }
                            sawSseData = true
                            val payload = line.removePrefix("data:").trim()
                            if (payload == "[DONE]") {
                                trySend(StreamEvent.Completed)
                                close()
                                return
                            }
                            if (payload.isEmpty()) continue
                            runCatching { veniceJson.decodeFromString<ChatCompletionResponse>(payload) }
                                .onSuccess { parsed ->
                                    parsed.choices.forEach { trySend(StreamEvent.Chunk(it.normalized())); emittedChunk = true }
                                }
                                .onFailure { lastDecodeError = it }
                        }

                        if (!sawSseData) {
                            // The body was never SSE. Try to read it as a normal (non-streamed)
                            // chat completion, then fall back to surfacing it as an error.
                            handleNonSseBody(rawBody.toString())
                        } else if (!emittedChunk && lastDecodeError != null) {
                            trySend(
                                StreamEvent.Failed(
                                    VeniceApiException(
                                        resp.code,
                                        "Couldn't parse Venice's streamed response: ${lastDecodeError?.message}",
                                    ),
                                ),
                            )
                        } else {
                            trySend(StreamEvent.Completed)
                        }
                    } catch (e: IOException) {
                        trySend(StreamEvent.Failed(e))
                    } finally {
                        close()
                    }
                }
            }

            private fun handleNonSseBody(raw: String) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) {
                    trySend(StreamEvent.Failed(VeniceApiException(0, "Venice returned an empty response.")))
                    return
                }
                val parsed = runCatching { veniceJson.decodeFromString<ChatCompletionResponse>(trimmed) }.getOrNull()
                val choices = parsed?.choices.orEmpty()
                if (choices.isNotEmpty() && choices.any { (it.message ?: it.delta)?.textOrNull()?.isNotBlank() == true || !it.message?.toolCalls.isNullOrEmpty() }) {
                    choices.forEach { trySend(StreamEvent.Chunk(it.normalized())) }
                    trySend(StreamEvent.Completed)
                } else {
                    // Not a usable completion — most likely a `{"error":...}` body returned as 200.
                    trySend(
                        StreamEvent.Failed(
                            VeniceApiException(0, "Venice returned an unexpected response: ${trimmed.take(MAX_RAW_BODY)}"),
                        ),
                    )
                }
            }
        })

        awaitClose { call.cancel() }
    }

    companion object {
        private const val MAX_RAW_BODY = 2000
    }
}

/** Normalizes a non-streamed choice (fields under `message`) into the streamed shape the rest of
 * the pipeline reads (`delta`), so a model that ignores `stream: true` still renders. */
private fun ChatChoice.normalized(): ChatChoice =
    if (delta == null && message != null) copy(delta = message) else this
