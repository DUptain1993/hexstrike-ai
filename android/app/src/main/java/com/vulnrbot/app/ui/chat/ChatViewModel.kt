package com.vulnrbot.app.ui.chat

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vulnrbot.app.VulnrBotApplication
import com.vulnrbot.app.data.agent.AgentEvent
import com.vulnrbot.app.data.agent.ApprovalRequest
import com.vulnrbot.app.data.agent.SystemPrompt
import com.vulnrbot.app.data.linux.LinuxSessionService
import com.vulnrbot.app.data.settings.AppSettings
import com.vulnrbot.app.data.venice.ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<VulnrBotApplication>()

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
            var toolSessionActive = false

            try {
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
                                currentAssistantId?.let { id ->
                                    updateUi<AssistantBubble>(id) { it.copy(text = event.text ?: it.text, streaming = false) }
                                }
                                currentAssistantId = null
                            }
                            is AgentEvent.ToolStarted -> {
                                if (!toolSessionActive) {
                                    toolSessionActive = true
                                    startToolSession()
                                }
                                appendUi(
                                    ToolBubble(
                                        id = event.callId,
                                        toolId = event.toolId,
                                        command = event.command,
                                        status = if (event.requiresApproval) ToolStatus.PENDING_APPROVAL else ToolStatus.RUNNING,
                                        output = "",
                                    ),
                                )
                            }
                            is AgentEvent.ToolOutputLine -> updateUi<ToolBubble>(event.callId) { it.copy(output = it.output + event.line + "\n") }
                            is AgentEvent.ToolFinished -> updateUi<ToolBubble>(event.callId) {
                                it.copy(
                                    status = if (event.result.exitCode == 0 && !event.result.timedOut) ToolStatus.SUCCESS else ToolStatus.FAILED,
                                    output = event.result.output,
                                )
                            }
                            is AgentEvent.Error -> appendUi(SystemNotice(newId(), event.message, isError = true))
                            AgentEvent.ModelToolSupportRejected -> {
                                app.settingsRepository.update { it.copy(selectedModelSupportsTools = false) }
                                appendUi(
                                    SystemNotice(
                                        newId(),
                                        "This model rejected native tool-calling, so Vulnr-Bot switched it to text-based tool calls automatically. This is remembered for next time.",
                                        isError = false,
                                    ),
                                )
                            }
                            AgentEvent.Done -> Unit
                        }
                    },
                    requestApproval = { request -> awaitApproval(request) },
                )
            } finally {
                if (toolSessionActive) stopToolSession()
                _isStreaming.value = false
            }

            persistNewMessages()
        }
    }

    /** Keeps the process alive (with a visible notification) while a security tool runs, so
     * Android doesn't kill a long-running nmap/sqlmap/hydra scan if the app is backgrounded. */
    private fun startToolSession() {
        val intent = Intent(app, LinuxSessionService::class.java)
            .putExtra(LinuxSessionService.EXTRA_LABEL, "Running security tool…")
        ContextCompat.startForegroundService(app, intent)
    }

    private fun stopToolSession() {
        app.stopService(Intent(app, LinuxSessionService::class.java))
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
