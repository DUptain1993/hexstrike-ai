package com.vulnrbot.app.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vulnrbot.app.VulnrBotApplication
import com.vulnrbot.app.data.linux.LinuxEnvironmentState
import com.vulnrbot.app.data.linux.LinuxInteractiveSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<VulnrBotApplication>()
    private val session = LinuxInteractiveSession(app.linuxEnvironmentRepository.paths, viewModelScope)

    val linuxState: StateFlow<LinuxEnvironmentState> = app.linuxEnvironmentRepository.state

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private var started = false

    init {
        viewModelScope.launch {
            session.output.collect { line ->
                _lines.value = (_lines.value + line).takeLast(MAX_LINES)
            }
        }
    }

    fun startIfNeeded() {
        if (started) return
        started = true
        session.start()
    }

    fun send(command: String) {
        _lines.value = _lines.value + "$ $command"
        session.send(command)
    }

    fun interrupt() = session.sendSignalInterrupt()

    override fun onCleared() {
        session.stop()
    }

    private companion object {
        const val MAX_LINES = 3000
    }
}
