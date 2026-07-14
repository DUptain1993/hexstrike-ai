package com.vulnrbot.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * All user-facing configuration, including the Venice AI API key, in one encrypted preferences
 * file. The key is stored via [EncryptedSharedPreferences] (AES-256) and is never included in
 * backups (see data_extraction_rules.xml).
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vulnrbot_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        persist(updated)
        _settings.value = updated
    }

    private fun loadSettings(): AppSettings = AppSettings(
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        baseUrl = prefs.getString(KEY_BASE_URL, AppSettings().baseUrl).orEmpty(),
        selectedModel = prefs.getString(KEY_MODEL, "").orEmpty(),
        selectedModelSupportsTools = prefs.getBoolean(KEY_MODEL_SUPPORTS_TOOLS, AppSettings().selectedModelSupportsTools),
        temperature = prefs.getFloat(KEY_TEMPERATURE, AppSettings().temperature),
        enableWebSearch = prefs.getBoolean(KEY_WEB_SEARCH, AppSettings().enableWebSearch),
        stripThinkingResponse = prefs.getBoolean(KEY_STRIP_THINKING, AppSettings().stripThinkingResponse),
        autoApproveTools = prefs.getBoolean(KEY_AUTO_APPROVE, AppSettings().autoApproveTools),
        onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING, false),
        linuxEnvironmentEnabled = prefs.getBoolean(KEY_LINUX_ENABLED, true),
        chrootPath = prefs.getString(KEY_CHROOT_PATH, AppSettings().chrootPath).orEmpty(),
        verboseNetworkLogging = prefs.getBoolean(KEY_VERBOSE_LOGGING, false),
    )

    private fun persist(s: AppSettings) {
        prefs.edit()
            .putString(KEY_API_KEY, s.apiKey)
            .putString(KEY_BASE_URL, s.baseUrl)
            .putString(KEY_MODEL, s.selectedModel)
            .putBoolean(KEY_MODEL_SUPPORTS_TOOLS, s.selectedModelSupportsTools)
            .putFloat(KEY_TEMPERATURE, s.temperature)
            .putBoolean(KEY_WEB_SEARCH, s.enableWebSearch)
            .putBoolean(KEY_STRIP_THINKING, s.stripThinkingResponse)
            .putBoolean(KEY_AUTO_APPROVE, s.autoApproveTools)
            .putBoolean(KEY_ONBOARDING, s.onboardingCompleted)
            .putBoolean(KEY_LINUX_ENABLED, s.linuxEnvironmentEnabled)
            .putString(KEY_CHROOT_PATH, s.chrootPath)
            .putBoolean(KEY_VERBOSE_LOGGING, s.verboseNetworkLogging)
            .apply()
    }

    private companion object {
        const val KEY_API_KEY = "venice_api_key"
        const val KEY_BASE_URL = "venice_base_url"
        const val KEY_MODEL = "venice_model"
        const val KEY_MODEL_SUPPORTS_TOOLS = "venice_model_supports_tools"
        const val KEY_TEMPERATURE = "venice_temperature"
        const val KEY_WEB_SEARCH = "venice_web_search"
        const val KEY_STRIP_THINKING = "venice_strip_thinking"
        const val KEY_AUTO_APPROVE = "agent_auto_approve"
        const val KEY_ONBOARDING = "onboarding_completed"
        const val KEY_LINUX_ENABLED = "linux_environment_enabled"
        const val KEY_CHROOT_PATH = "chroot_path"
        const val KEY_VERBOSE_LOGGING = "verbose_network_logging"
    }
}
