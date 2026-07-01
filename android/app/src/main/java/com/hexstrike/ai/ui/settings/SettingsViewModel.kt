package com.hexstrike.ai.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hexstrike.ai.HexStrikeApplication
import com.hexstrike.ai.data.linux.LinuxEnvironmentState
import com.hexstrike.ai.data.settings.AppSettings
import com.hexstrike.ai.data.venice.VeniceModel
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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<HexStrikeApplication>()

    val settings: StateFlow<AppSettings> = app.settingsRepository.settings
    val linuxState: StateFlow<LinuxEnvironmentState> = app.linuxEnvironmentRepository.state

    private val _modelsState = MutableStateFlow<ModelsLoadState>(ModelsLoadState.Idle)
    val modelsState: StateFlow<ModelsLoadState> = _modelsState.asStateFlow()

    fun updateApiKey(key: String) = app.settingsRepository.update { it.copy(apiKey = key) }
    fun updateBaseUrl(url: String) = app.settingsRepository.update { it.copy(baseUrl = url) }
    fun updateModel(modelId: String) = app.settingsRepository.update { it.copy(selectedModel = modelId) }
    fun updateTemperature(value: Float) = app.settingsRepository.update { it.copy(temperature = value) }
    fun setWebSearch(enabled: Boolean) = app.settingsRepository.update { it.copy(enableWebSearch = enabled) }
    fun setStripThinking(enabled: Boolean) = app.settingsRepository.update { it.copy(stripThinkingResponse = enabled) }
    fun setAutoApprove(enabled: Boolean) = app.settingsRepository.update { it.copy(autoApproveTools = enabled) }
    fun setLinuxEnabled(enabled: Boolean) = app.settingsRepository.update { it.copy(linuxEnvironmentEnabled = enabled) }
    fun completeOnboarding() = app.settingsRepository.update { it.copy(onboardingCompleted = true) }

    fun testConnectionAndLoadModels() {
        viewModelScope.launch {
            _modelsState.value = ModelsLoadState.Loading
            app.veniceRepository.configure(settings.value.apiKey, settings.value.baseUrl, settings.value.verboseNetworkLogging)
            app.veniceRepository.listModels()
                .onSuccess { models ->
                    _modelsState.value = ModelsLoadState.Loaded(models.sortedByDescending { it.supportsTools })
                    if (settings.value.selectedModel.isBlank() && models.isNotEmpty()) {
                        updateModel((models.firstOrNull { it.supportsTools } ?: models.first()).id)
                    }
                }
                .onFailure { error -> _modelsState.value = ModelsLoadState.Error(error.message ?: "Failed to reach Venice AI") }
        }
    }

    fun installLinuxEnvironment() {
        viewModelScope.launch { app.linuxEnvironmentRepository.install() }
    }

    fun refreshLinuxState() = app.linuxEnvironmentRepository.refreshState()
}
