package com.vulnrbot.app.data.agent

import com.vulnrbot.app.data.settings.AppSettings
import com.vulnrbot.app.data.tools.SecurityToolRegistry
import com.vulnrbot.app.data.tools.ToolExecutionResult
import com.vulnrbot.app.data.tools.ToolExecutor
import com.vulnrbot.app.data.venice.ChatCompletionRequest
import com.vulnrbot.app.data.venice.ChatMessage
import com.vulnrbot.app.data.venice.ChatStreamAccumulator
import com.vulnrbot.app.data.venice.StreamEvent
import com.vulnrbot.app.data.venice.VeniceParameters
import com.vulnrbot.app.data.venice.VeniceRepository

/**
 * Drives one user turn to completion: sends the conversation (+ tool schemas) to Venice AI,
 * streams the reply, and whenever the model asks for a tool call, resolves the command, asks the
 * UI to approve it (unless auto-approve is on or the tool is marked as safe), executes it, and
 * feeds the result back — repeating until the model stops requesting tools or a safety cap hits.
 *
 * Two tool-calling paths are supported:
 *  - Native: the model reports function-calling support, so we use Venice's structured `tools`
 *    request field and its `tool_calls` response field directly.
 *  - Prompt-based: Venice rejects the whole request if `tools` is sent to a model that doesn't
 *    support it — that's a limitation of Venice's API surface, not proof the model can't reason
 *    about tool use — so instead we describe the tools in the system prompt and parse a
 *    `<tool_call>{...}</tool_call>` text convention out of the reply (see
 *    [SystemPrompt.promptBasedToolingInstructions] / [PromptToolCallParser]).
 */
class AgentOrchestrator(
    private val venice: VeniceRepository,
    private val toolExecutor: ToolExecutor,
) {

    suspend fun runTurn(
        history: MutableList<ChatMessage>,
        settings: AppSettings,
        emit: (AgentEvent) -> Unit,
        requestApproval: suspend (ApprovalRequest) -> Boolean,
    ) {
        var forcePromptBasedTools = false

        var iterations = 0
        while (iterations < MAX_TOOL_ITERATIONS) {
            iterations++

            val useNativeTools = settings.linuxEnvironmentEnabled && settings.selectedModelSupportsTools && !forcePromptBasedTools
            val usePromptBasedTools = settings.linuxEnvironmentEnabled && (!settings.selectedModelSupportsTools || forcePromptBasedTools)

            val request = ChatCompletionRequest(
                model = settings.selectedModel,
                messages = if (usePromptBasedTools) withPromptToolInstructions(history) else history,
                stream = true,
                temperature = settings.temperature.toDouble(),
                tools = if (useNativeTools) SecurityToolRegistry.toolDefinitions() else null,
                parallelToolCalls = false,
                veniceParameters = VeniceParameters(
                    enableWebSearch = if (settings.enableWebSearch) "auto" else "off",
                    stripThinkingResponse = settings.stripThinkingResponse,
                ),
            )

            val accumulator = ChatStreamAccumulator()
            var streamError: Throwable? = null

            venice.streamChatCompletion(request).collect { event ->
                when (event) {
                    is StreamEvent.Chunk -> {
                        accumulator.accept(event.choice)
                        event.choice.delta?.textOrNull()?.let { emit(AgentEvent.AssistantTextDelta(it)) }
                    }
                    is StreamEvent.Failed -> streamError = event.error
                    StreamEvent.Completed -> Unit
                }
            }

            if (streamError != null) {
                val message = streamError?.message.orEmpty()
                // Venice's /models endpoint can say a model supports function calling and still
                // reject the actual `tools` field — seen twice for unrelated-looking reasons (an
                // outright "not supported" response, and a mysterious per-request rejection
                // despite well-formed schemas well under any documented limit). Rather than fail
                // the whole turn on something the user can't do anything about, retry once with
                // the text-based fallback convention and remember not to try native tools again
                // for the rest of this turn.
                if (useNativeTools && message.contains("tools", ignoreCase = true)) {
                    forcePromptBasedTools = true
                    emit(AgentEvent.ModelToolSupportRejected)
                    iterations--
                    continue
                }
                emit(AgentEvent.Error(message.ifBlank { "Venice AI request failed" }))
                return
            }

            val assistantMessage = accumulator.buildMessage()
            history.add(assistantMessage)

            val nativeToolCalls = assistantMessage.toolCalls
            val promptToolCall = if (usePromptBasedTools && nativeToolCalls.isNullOrEmpty()) {
                PromptToolCallParser.extract(assistantMessage.textOrNull().orEmpty())
            } else {
                null
            }

            val displayText = if (promptToolCall != null) {
                PromptToolCallParser.stripToolCallBlock(assistantMessage.textOrNull().orEmpty())
            } else {
                assistantMessage.textOrNull()
            }
            emit(AgentEvent.AssistantMessageFinal(displayText))

            if (!nativeToolCalls.isNullOrEmpty()) {
                for (call in nativeToolCalls) {
                    val toolId = call.function?.name ?: continue
                    val callId = call.id ?: toolId
                    val result = runTool(toolId, call.function.arguments ?: "{}", callId, settings, emit, requestApproval)
                    history.add(ChatMessage.toolResult(callId, result.output))
                }
                continue
            }

            if (promptToolCall != null) {
                val callId = "call_${iterations}_${System.nanoTime()}"
                val result = runTool(promptToolCall.name, promptToolCall.argumentsJson, callId, settings, emit, requestApproval)
                // Plain user-role feedback rather than the "tool" role: this whole path exists for
                // models Venice won't let use the structured tool/function-calling machinery at
                // all, so stick to the one message shape guaranteed to work everywhere.
                history.add(ChatMessage.user("Tool result for ${promptToolCall.name}:\n${result.output}"))
                continue
            }

            emit(AgentEvent.Done)
            return
        }

        emit(AgentEvent.Error("Stopped after $MAX_TOOL_ITERATIONS tool calls in a row to avoid a runaway loop."))
    }

    private suspend fun runTool(
        toolId: String,
        argumentsJson: String,
        callId: String,
        settings: AppSettings,
        emit: (AgentEvent) -> Unit,
        requestApproval: suspend (ApprovalRequest) -> Boolean,
    ): ToolExecutionResult {
        val execution = toolExecutor.prepare(toolId, argumentsJson)
        val needsApproval = execution.tool?.requiresConfirmation == true &&
            !execution.command.startsWith("#") &&
            !settings.autoApproveTools
        emit(AgentEvent.ToolStarted(callId, toolId, execution.command, needsApproval))
        val approved = !needsApproval || requestApproval(ApprovalRequest(callId, execution))

        val result = if (approved) {
            toolExecutor.execute(execution) { line -> emit(AgentEvent.ToolOutputLine(callId, line)) }
        } else {
            ToolExecutionResult("The user denied permission to run this command.", exitCode = -1, timedOut = false)
        }
        emit(AgentEvent.ToolFinished(callId, toolId, result))
        return result
    }

    /** Appends the tool catalog + calling convention to the outgoing request's system message
     * without mutating the persisted [history] — cheap enough to redo on every turn, and keeps
     * the tool listing out of what actually gets saved to the chat history / sent to
     * [SecurityToolRegistry] callers elsewhere. */
    private fun withPromptToolInstructions(history: List<ChatMessage>): List<ChatMessage> {
        val instructions = SystemPrompt.promptBasedToolingInstructions()
        if (history.isEmpty()) return history
        val first = history[0]
        if (first.role != "system") {
            return buildList {
                add(ChatMessage.system(instructions))
                addAll(history)
            }
        }
        val merged = ((first.textOrNull() ?: "") + "\n\n" + instructions).trim()
        return buildList {
            add(ChatMessage.system(merged))
            addAll(history.drop(1))
        }
    }

    companion object {
        const val MAX_TOOL_ITERATIONS = 15
    }
}
