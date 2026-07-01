package com.hexstrike.ai.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hexstrike.ai.HexStrikeApplication
import com.hexstrike.ai.data.agent.AgentEvent
import com.hexstrike.ai.data.agent.ApprovalRequest
import com.hexstrike.ai.data.agent.SystemPrompt
import com.hexstrike.ai.data.settings.AppSettings
import com.hexstrike.ai.data.venice.ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<HexStrikeApplication>()

    val settings: StateFlow<AppSettings> = app.settingsRepository.settings

    private var sessionId: Long? = null
    private var history: MutableList<ChatMessage> = mutableListOf()
    private var persistedCount = 0

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ApprovalRequest?>(null)
    val pendingApproval: StateFlow<ApprovalRequest?> = _pendingApproval.asStateFlow()

    private var approvalDeferred: CompletableDeferred<Boolean>? = null

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isStreaming.value) return
        if (!settings.value.isReadyToChat) {
            appendUi(SystemNotice(newId(), "Add a Venice AI API key and pick a model in Settings first.", isError = true))
            return
        }

        viewModelScope.launch {
            ensureSession()
            if (history.isEmpty()) history.add(ChatMessage.system(SystemPrompt.DEFAULT))

            val userMessage = ChatMessage.user(trimmed)
            history.add(userMessage)
            appendUi(UserBubble(newId(), trimmed))

            _isStreaming.value = true
            var currentAssistantId: String? = null

            app.agentOrchestrator.runTurn(
                history = history,
                settings = settings.value,
                emit = { event ->
                    when (event) {
                        is AgentEvent.AssistantTextDelta -> {
                            val id = currentAssistantId
                            if (id == null) {
                                val newBubbleId = newId()
                                currentAssistantId = newBubbleId
                                appendUi(AssistantBubble(newBubbleId, event.delta, streaming = true))
                            } else {
                                updateUi<AssistantBubble>(id) { it.copy(text = it.text + event.delta) }
                            }
                        }
                        is AgentEvent.AssistantMessageFinal -> {
                            currentAssistantId?.let { id -> updateUi<AssistantBubble>(id) { it.copy(streaming = false) } }
                            currentAssistantId = null
                        }
                        is AgentEvent.ToolStarted -> appendUi(
                            ToolBubble(
                                id = event.callId,
                                toolId = event.toolId,
                                command = event.command,
                                status = if (event.requiresApproval) ToolStatus.PENDING_APPROVAL else ToolStatus.RUNNING,
                                output = "",
                            ),
                        )
                        is AgentEvent.ToolOutputLine -> updateUi<ToolBubble>(event.callId) { it.copy(output = it.output + event.line + "\n") }
                        is AgentEvent.ToolFinished -> updateUi<ToolBubble>(event.callId) {
                            it.copy(
                                status = if (event.result.exitCode == 0 && !event.result.timedOut) ToolStatus.SUCCESS else ToolStatus.FAILED,
                                output = event.result.output,
                            )
                        }
                        is AgentEvent.Error -> appendUi(SystemNotice(newId(), event.message, isError = true))
                        AgentEvent.Done -> Unit
                    }
                },
                requestApproval = { request -> awaitApproval(request) },
            )

            _isStreaming.value = false
            persistNewMessages()
        }
    }

    fun respondToApproval(approved: Boolean) {
        val callId = pendingApproval.value?.callId ?: return
        updateUi<ToolBubble>(callId) { it.copy(status = if (approved) ToolStatus.RUNNING else ToolStatus.DENIED) }
        approvalDeferred?.complete(approved)
        _pendingApproval.value = null
    }

    fun newSession() {
        sessionId = null
        history = mutableListOf()
        persistedCount = 0
        _messages.value = emptyList()
    }

    private suspend fun awaitApproval(request: ApprovalRequest): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        approvalDeferred = deferred
        _pendingApproval.value = request
        return deferred.await()
    }

    private suspend fun ensureSession() {
        if (sessionId == null) {
            sessionId = app.chatHistoryRepository.createSession()
        }
    }

    private suspend fun persistNewMessages() {
        val id = sessionId ?: return
        while (persistedCount < history.size) {
            app.chatHistoryRepository.appendMessage(id, history[persistedCount])
            persistedCount++
        }
    }

    private fun newId(): String = "m-" + System.nanoTime()

    private fun appendUi(message: UiMessage) {
        _messages.value = _messages.value + message
    }

    private inline fun <reified T : UiMessage> updateUi(id: String, transform: (T) -> T) {
        _messages.value = _messages.value.map { if (it.id == id && it is T) transform(it) else it }
    }
}
