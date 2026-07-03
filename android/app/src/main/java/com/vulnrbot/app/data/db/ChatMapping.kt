package com.vulnrbot.app.data.db

import com.vulnrbot.app.data.venice.ChatMessage
import com.vulnrbot.app.data.venice.ToolCall
import com.vulnrbot.app.data.venice.veniceJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive

fun ChatMessageEntity.toChatMessage(): ChatMessage = ChatMessage(
    role = role,
    content = contentText?.let { JsonPrimitive(it) },
    toolCallId = toolCallId,
    toolCalls = toolCallsJson?.let { runCatching { veniceJson.decodeFromString<List<ToolCall>>(it) }.getOrNull() },
)

fun ChatMessage.toEntity(sessionId: Long, orderIndex: Int): ChatMessageEntity = ChatMessageEntity(
    sessionId = sessionId,
    orderIndex = orderIndex,
    role = role,
    contentText = textOrNull(),
    toolCallId = toolCallId,
    toolCallsJson = toolCalls?.let { veniceJson.encodeToString(it) },
    createdAt = System.currentTimeMillis(),
)
