package com.hexstrike.ai.data.db

import android.content.Context
import com.hexstrike.ai.data.venice.ChatMessage
import kotlinx.coroutines.flow.Flow

class ChatHistoryRepository(context: Context) {
    private val dao = HexStrikeDatabase.getInstance(context).chatDao()

    fun observeSessions(): Flow<List<ChatSessionEntity>> = dao.observeSessions()

    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>> = dao.observeMessages(sessionId)

    suspend fun createSession(title: String = "New session"): Long {
        val now = System.currentTimeMillis()
        return dao.insertSession(ChatSessionEntity(title = title, createdAt = now, updatedAt = now))
    }

    suspend fun deleteSession(session: ChatSessionEntity) = dao.deleteSession(session)

    suspend fun renameSession(sessionId: Long, title: String) = dao.renameSession(sessionId, title)

    suspend fun loadHistory(sessionId: Long): MutableList<ChatMessage> =
        dao.getMessages(sessionId).map { it.toChatMessage() }.toMutableList()

    suspend fun appendMessage(sessionId: Long, message: ChatMessage) {
        val nextIndex = dao.maxOrderIndex(sessionId) + 1
        dao.insertMessage(message.toEntity(sessionId, nextIndex))
        dao.touchSession(sessionId, System.currentTimeMillis())
    }
}
