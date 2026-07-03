package com.vulnrbot.app

import android.app.Application
import com.vulnrbot.app.data.agent.AgentOrchestrator
import com.vulnrbot.app.data.db.ChatHistoryRepository
import com.vulnrbot.app.data.linux.LinuxEnvironmentRepository
import com.vulnrbot.app.data.settings.SettingsRepository
import com.vulnrbot.app.data.tools.ToolExecutor
import com.vulnrbot.app.data.tools.ToolInstaller
import com.vulnrbot.app.data.venice.VeniceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Simple hand-rolled DI container: one instance of each repository/service for the app's
 * lifetime. Small enough surface area that pulling in Hilt/Dagger wasn't worth the build
 * complexity. */
class VulnrBotApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var veniceRepository: VeniceRepository
        private set
    lateinit var linuxEnvironmentRepository: LinuxEnvironmentRepository
        private set
    lateinit var toolExecutor: ToolExecutor
        private set
    lateinit var toolInstaller: ToolInstaller
        private set
    lateinit var agentOrchestrator: AgentOrchestrator
        private set
    lateinit var chatHistoryRepository: ChatHistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()

        settingsRepository = SettingsRepository(this)
        veniceRepository = VeniceRepository()
        linuxEnvironmentRepository = LinuxEnvironmentRepository(this)
        toolExecutor = ToolExecutor(linuxEnvironmentRepository.shell)
        toolInstaller = ToolInstaller(linuxEnvironmentRepository.shell)
        agentOrchestrator = AgentOrchestrator(veniceRepository, toolExecutor)
        chatHistoryRepository = ChatHistoryRepository(this)

        applicationScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                veniceRepository.configure(
                    apiKey = settings.apiKey,
                    baseUrl = settings.baseUrl,
                    verboseLogging = settings.verboseNetworkLogging,
                )
            }
        }
    }
}
