package com.hexstrike.ai.ui.chat

enum class ToolStatus { PENDING_APPROVAL, RUNNING, SUCCESS, FAILED, DENIED }

sealed interface UiMessage {
    val id: String
}

data class UserBubble(override val id: String, val text: String) : UiMessage

data class AssistantBubble(override val id: String, val text: String, val streaming: Boolean) : UiMessage

data class ToolBubble(
    override val id: String,
    val toolId: String,
    val command: String,
    val status: ToolStatus,
    val output: String,
) : UiMessage

data class SystemNotice(override val id: String, val text: String, val isError: Boolean = false) : UiMessage
