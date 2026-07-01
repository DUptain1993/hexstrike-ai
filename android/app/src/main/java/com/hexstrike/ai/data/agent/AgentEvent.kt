package com.hexstrike.ai.data.agent

import com.hexstrike.ai.data.tools.ToolExecution
import com.hexstrike.ai.data.tools.ToolExecutionResult

sealed interface AgentEvent {
    data class AssistantTextDelta(val delta: String) : AgentEvent
    data class AssistantMessageFinal(val text: String?) : AgentEvent
    data class ToolStarted(val callId: String, val toolId: String, val command: String, val requiresApproval: Boolean) : AgentEvent
    data class ToolOutputLine(val callId: String, val line: String) : AgentEvent
    data class ToolFinished(val callId: String, val toolId: String, val result: ToolExecutionResult) : AgentEvent
    data class Error(val message: String) : AgentEvent
    data object Done : AgentEvent
}

/** Raised to the UI so it can render an approve/deny prompt before a tool actually executes. */
data class ApprovalRequest(val callId: String, val execution: ToolExecution)
