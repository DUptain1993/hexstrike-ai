package com.vulnrbot.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vulnrbot.app.VulnrBotApplication
import com.vulnrbot.app.data.linux.LinuxEnvironmentState
import com.vulnrbot.app.data.settings.AppSettings
import com.vulnrbot.app.data.tools.InstallProgress
import com.vulnrbot.app.data.tools.SecurityToolRegistry
import com.vulnrbot.app.data.venice.VeniceModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ModelsLoadState {
    data object Idle : ModelsLoadState
    data object Loading : ModelsLoadState
    data class Loaded(val models: List<VeniceModel>) : ModelsLoadState
    data class Error(val message: String) : ModelsLoadState
}

data class ToolInstallResult(val toolId: String, val success: Boolean, val message: String)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<VulnrBotApplication>()

    val settings: StateFlow<AppSettings> = app.settingsRepository.settings
    val linuxState: StateFlow<LinuxEnvironmentState> = app.linuxEnvironmentRepository.state

    private val _modelsState = MutableStateFlow<ModelsLoadState>(ModelsLoadState.Idle)
    val modelsState: StateFlow<ModelsLoadState> = _modelsState.asStateFlow()

    private val _toolInstallRunning = MutableStateFlow(false)
    val toolInstallRunning: StateFlow<Boolean> = _toolInstallRunning.asStateFlow()

    private val _toolInstallCurrent = MutableStateFlow<String?>(null)
    val toolInstallCurrent: StateFlow<String?> = _toolInstallCurrent.asStateFlow()

    private val _toolInstallResults = MutableStateFlow<List<ToolInstallResult>>(emptyList())
    val toolInstallResults: StateFlow<List<ToolInstallResult>> = _toolInstallResults.asStateFlow()

    private val _diagnostics = MutableStateFlow<String?>(null)
    val diagnostics: StateFlow<String?> = _diagnostics.asStateFlow()

    private val _diagnosticsRunning = MutableStateFlow(false)
    val diagnosticsRunning: StateFlow<Boolean> = _diagnosticsRunning.asStateFlow()

    fun updateApiKey(key: String) = app.settingsRepository.update { it.copy(apiKey = key) }
    fun updateBaseUrl(url: String) = app.settingsRepository.update { it.copy(baseUrl = url) }
    fun updateModel(model: VeniceModel) = app.settingsRepository.update {
        it.copy(selectedModel = model.id, selectedModelSupportsTools = model.supportsTools)
    }
    fun updateTemperature(value: Float) = app.settingsRepository.update { it.copy(temperature = value) }
    fun setWebSearch(enabled: Boolean) = app.settingsRepository.update { it.copy(enableWebSearch = enabled) }
    fun setStripThinking(enabled: Boolean) = app.settingsRepository.update { it.copy(stripThinkingResponse = enabled) }
    fun setAutoApprove(enabled: Boolean) = app.settingsRepository.update { it.copy(autoApproveTools = enabled) }
    fun setLinuxEnabled(enabled: Boolean) = app.settingsRepository.update { it.copy(linuxEnvironmentEnabled = enabled) }
    fun updateChrootPath(path: String) = app.settingsRepository.update { it.copy(chrootPath = path) }
    fun completeOnboarding() = app.settingsRepository.update { it.copy(onboardingCompleted = true) }

    fun testRootAndChroot() {
        if (_diagnosticsRunning.value) return
        viewModelScope.launch {
            _diagnosticsRunning.value = true
            _diagnostics.value = "Running…"
            _diagnostics.value = runCatching { app.linuxEnvironmentRepository.testRootAndChroot() }
                .getOrElse { "Diagnostic failed: ${it.message}" }
            _diagnosticsRunning.value = false
            app.linuxEnvironmentRepository.refreshState()
        }
    }

    fun testConnectionAndLoadModels() {
        viewModelScope.launch {
            _modelsState.value = ModelsLoadState.Loading
            app.veniceRepository.configure(settings.value.apiKey, settings.value.baseUrl, settings.value.verboseNetworkLogging)
            app.veniceRepository.listModels()
                .onSuccess { models ->
                    _modelsState.value = ModelsLoadState.Loaded(models.sortedByDescending { it.supportsTools })
                    if (settings.value.selectedModel.isBlank() && models.isNotEmpty()) {
                        updateModel(models.firstOrNull { it.supportsTools } ?: models.first())
                    }
                }
                .onFailure { error -> _modelsState.value = ModelsLoadState.Error(error.message ?: "Failed to reach Venice AI") }
        }
    }

    fun installLinuxEnvironment() {
        viewModelScope.launch { app.linuxEnvironmentRepository.install() }
    }

    fun refreshLinuxState() = app.linuxEnvironmentRepository.refreshState()

    fun installSecurityTools() {
        if (_toolInstallRunning.value) return
        viewModelScope.launch {
            _toolInstallRunning.value = true
            _toolInstallResults.value = emptyList()
            app.toolInstaller.installTools(SecurityToolRegistry.recommendedCoreToolIds).collect { progress ->
                when (progress) {
                    is InstallProgress.Installing -> _toolInstallCurrent.value = progress.toolId
                    is InstallProgress.ToolResult -> _toolInstallResults.value =
                        _toolInstallResults.value + ToolInstallResult(progress.toolId, progress.success, progress.message)
                    InstallProgress.Done -> {
                        _toolInstallCurrent.value = null
                        _toolInstallRunning.value = false
                    }
                }
            }
        }
    }
}
