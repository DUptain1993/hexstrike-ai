package com.vulnrbot.app.data.venice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Wire models for Venice AI's OpenAI-compatible API (https://docs.venice.ai/api-reference).
 * Confirmed against the live API: POST /api/v1/chat/completions, GET /api/v1/models,
 * standard `Authorization: Bearer <key>` auth, plus the `venice_parameters` extension block.
 */

@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement? = null,
    val name: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
) {
    companion object {
        fun system(text: String) = ChatMessage(role = "system", content = JsonPrimitive(text))
        fun user(text: String) = ChatMessage(role = "user", content = JsonPrimitive(text))
        fun assistant(text: String?, toolCalls: List<ToolCall>? = null) =
            ChatMessage(role = "assistant", content = text?.let { JsonPrimitive(it) }, toolCalls = toolCalls)

        fun toolResult(toolCallId: String, content: String) =
            ChatMessage(role = "tool", toolCallId = toolCallId, content = JsonPrimitive(content))
    }

    /** Best-effort plain-text extraction; multimodal parts are ignored. */
    fun textOrNull(): String? = (content as? JsonPrimitive)?.takeIf { it.isString }?.content
}

@Serializable
data class ToolCall(
    val id: String? = null,
    val index: Int? = null,
    val type: String? = "function",
    val function: FunctionCallData? = null,
)

@Serializable
data class FunctionCallData(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionSpec,
)

@Serializable
data class FunctionSpec(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement,
    val strict: Boolean? = null,
)

@Serializable
data class VeniceParameters(
    @SerialName("enable_web_search") val enableWebSearch: String? = null,
    @SerialName("enable_web_citations") val enableWebCitations: Boolean? = null,
    @SerialName("include_venice_system_prompt") val includeVeniceSystemPrompt: Boolean? = null,
    @SerialName("strip_thinking_response") val stripThinkingResponse: Boolean? = null,
    @SerialName("disable_thinking") val disableThinking: Boolean? = null,
)

@Serializable
data class ReasoningConfig(
    val effort: String? = null,
)

@Serializable
data class StreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean = true,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    @SerialName("stream_options") val streamOptions: StreamOptions? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
    @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
    @SerialName("venice_parameters") val veniceParameters: VeniceParameters? = null,
    val reasoning: ReasoningConfig? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
data class ModelCapabilities(
    @SerialName("supportsFunctionCalling") val supportsFunctionCalling: Boolean = false,
    @SerialName("supportsReasoning") val supportsReasoning: Boolean = false,
    @SerialName("supportsVision") val supportsVision: Boolean = false,
    @SerialName("supportsWebSearch") val supportsWebSearch: Boolean = false,
    @SerialName("optimizedForCode") val optimizedForCode: Boolean = false,
)

@Serializable
data class ModelSpec(
    val name: String? = null,
    val description: String? = null,
    val capabilities: ModelCapabilities? = null,
    @SerialName("availableContextTokens") val availableContextTokens: Int? = null,
    @SerialName("maxCompletionTokens") val maxCompletionTokens: Int? = null,
)

@Serializable
data class VeniceModel(
    val id: String,
    val type: String? = null,
    @SerialName("owned_by") val ownedBy: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("model_spec") val modelSpec: ModelSpec? = null,
) {
    val displayName: String get() = modelSpec?.name ?: id
    val supportsTools: Boolean get() = modelSpec?.capabilities?.supportsFunctionCalling == true
}

@Serializable
data class ModelsResponse(
    val data: List<VeniceModel> = emptyList(),
)

class VeniceApiException(val httpCode: Int, message: String) : Exception(message)
