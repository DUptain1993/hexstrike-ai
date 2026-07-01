package com.hexstrike.ai.data.agent

import com.hexstrike.ai.data.settings.AppSettings
import com.hexstrike.ai.data.tools.SecurityToolRegistry
import com.hexstrike.ai.data.tools.ToolExecutionResult
import com.hexstrike.ai.data.tools.ToolExecutor
import com.hexstrike.ai.data.venice.ChatCompletionRequest
import com.hexstrike.ai.data.venice.ChatMessage
import com.hexstrike.ai.data.venice.ChatStreamAccumulator
import com.hexstrike.ai.data.venice.StreamEvent
import com.hexstrike.ai.data.venice.VeniceParameters
import com.hexstrike.ai.data.venice.VeniceRepository

/**
 * Drives one user turn to completion: sends the conversation (+ tool schemas) to Venice AI,
 * streams the reply, and whenever the model asks for a tool call, resolves the command, asks the
 * UI to approve it (unless auto-approve is on or the tool is marked as safe), executes it, and
 * feeds the result back — repeating until the model stops requesting tools or a safety cap hits.
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
        var iterations = 0
        while (iterations < MAX_TOOL_ITERATIONS) {
            iterations++

            val request = ChatCompletionRequest(
                model = settings.selectedModel,
                messages = history,
                stream = true,
                temperature = settings.temperature.toDouble(),
                tools = if (settings.linuxEnvironmentEnabled) SecurityToolRegistry.toolDefinitions() else null,
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
                emit(AgentEvent.Error(streamError?.message ?: "Venice AI request failed"))
                return
            }

            val assistantMessage = accumulator.buildMessage()
            history.add(assistantMessage)
            emit(AgentEvent.AssistantMessageFinal(assistantMessage.textOrNull()))

            val toolCalls = assistantMessage.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                emit(AgentEvent.Done)
                return
            }

            for (call in toolCalls) {
                val toolId = call.function?.name ?: continue
                val callId = call.id ?: toolId
                val execution = toolExecutor.prepare(toolId, call.function.arguments ?: "{}")
                val needsApproval = execution.tool?.requiresConfirmation == true &&
                    !execution.command.startsWith("#") &&
                    !settings.autoApproveTools
                emit(AgentEvent.ToolStarted(callId, toolId, execution.command, needsApproval))
                val approved = !needsApproval || requestApproval(ApprovalRequest(callId, execution))

                val result = if (approved) {
                    toolExecutor.execute(execution) { line -> emit(AgentEvent.ToolOutputLine(callId, line)) }
                } else {
                    ToolExecutionResult(
                        "The user denied permission to run this command.",
                        exitCode = -1,
                        timedOut = false,
                    )
                }

                emit(AgentEvent.ToolFinished(callId, toolId, result))
                history.add(ChatMessage.toolResult(callId, result.output))
            }
        }

        emit(AgentEvent.Error("Stopped after $MAX_TOOL_ITERATIONS tool calls in a row to avoid a runaway loop."))
    }

    companion object {
        const val MAX_TOOL_ITERATIONS = 15
    }
}
