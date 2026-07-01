package com.hexstrike.ai.data.settings

import com.hexstrike.ai.data.venice.VeniceClientFactory

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = VeniceClientFactory.DEFAULT_BASE_URL,
    val selectedModel: String = "",
    val selectedModelSupportsTools: Boolean = true,
    val temperature: Float = 0.8f,
    val enableWebSearch: Boolean = false,
    val stripThinkingResponse: Boolean = true,
    val autoApproveTools: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val linuxEnvironmentEnabled: Boolean = true,
    val verboseNetworkLogging: Boolean = false,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val hasModel: Boolean get() = selectedModel.isNotBlank()
    val isReadyToChat: Boolean get() = hasApiKey && hasModel
}
