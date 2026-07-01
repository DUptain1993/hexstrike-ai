package com.hexstrike.ai.data.venice

/**
 * OpenAI-style streaming sends the assistant's reply and any tool calls as a sequence of
 * fragments keyed by index. This merges those fragments back into one coherent [ChatMessage].
 */
class ChatStreamAccumulator {
    private val contentBuilder = StringBuilder()
    private val toolCallBuilders = sortedMapOf<Int, ToolCallBuilder>()
    private var finishReason: String? = null

    private class ToolCallBuilder {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    fun accept(choice: ChatChoice) {
        choice.finishReason?.let { finishReason = it }
        val delta = choice.delta ?: return
        delta.textOrNull()?.let { contentBuilder.append(it) }
        delta.toolCalls?.forEach { call ->
            val builder = toolCallBuilders.getOrPut(call.index ?: 0) { ToolCallBuilder() }
            call.id?.let { builder.id = it }
            call.function?.name?.let { builder.name = it }
            call.function?.arguments?.let { builder.arguments.append(it) }
        }
    }

    fun currentText(): String = contentBuilder.toString()

    fun isToolCall(): Boolean = finishReason == "tool_calls" || toolCallBuilders.isNotEmpty()

    fun finishReasonOrNull(): String? = finishReason

    fun buildMessage(): ChatMessage {
        val toolCalls = toolCallBuilders.entries.sortedBy { it.key }.map { (index, builder) ->
            ToolCall(
                id = builder.id ?: "call_$index",
                type = "function",
                function = FunctionCallData(name = builder.name, arguments = builder.arguments.toString()),
            )
        }.ifEmpty { null }
        return ChatMessage.assistant(contentBuilder.toString().ifBlank { null }, toolCalls)
    }
}
