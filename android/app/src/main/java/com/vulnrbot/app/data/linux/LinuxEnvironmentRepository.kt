package com.vulnrbot.app.data.linux

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Top-level facade over the Linux subsystem: owns the rootfs download/install flow and exposes
 * the [LinuxShell] once it's ready. One instance lives for the app's lifetime (see
 * VulnrBotApplication). */
class LinuxEnvironmentRepository(context: Context) {

    val paths = LinuxEnvironmentPaths(context)
    private val downloader = RootfsDownloader(paths)
    private val prootManager = ProotManager(paths)
    val shell = LinuxShell(paths)

    private val _state = MutableStateFlow(computeInitialState())
    val state: StateFlow<LinuxEnvironmentState> = _state.asStateFlow()

    private fun computeInitialState(): LinuxEnvironmentState {
        if (!paths.prootBinary.exists()) return LinuxEnvironmentState.Unavailable
        return if (prootManager.isAvailable()) LinuxEnvironmentState.Ready else LinuxEnvironmentState.NotInstalled
    }

    fun refreshState() {
        _state.value = computeInitialState()
    }

    suspend fun install(release: String = RootfsDownloader.DEFAULT_RELEASE) {
        if (!paths.prootBinary.exists()) {
            _state.value = LinuxEnvironmentState.Unavailable
            return
        }
        try {
            downloader.install(release).collect { progress ->
                _state.value = when (progress) {
                    is RootfsDownloader.Progress.Downloading ->
                        LinuxEnvironmentState.Downloading(progress.bytesRead, progress.totalBytes)
                    RootfsDownloader.Progress.Extracting -> LinuxEnvironmentState.Extracting
                    RootfsDownloader.Progress.Done -> LinuxEnvironmentState.Configuring
                }
            }
            runBaselineSetup()
            _state.value = LinuxEnvironmentState.Ready
        } catch (e: Exception) {
            _state.value = LinuxEnvironmentState.Error(e.message ?: "Unknown error setting up the Linux environment")
        }
    }

    private suspend fun runBaselineSetup() {
        shell.exec("apt-get update -y", timeoutMs = 10 * 60 * 1000)
        shell.installPackagesIndividually(BASELINE_PACKAGES)
    }

    companion object {
        /** Installed one at a time — see [LinuxShell.installPackagesIndividually] for why that
         * matters here specifically: git/go/pip3 are load-bearing for most of the tool catalog's
         * install commands, so a single unrelated package's postinst failure taking the whole
         * batch down with it is worse here than almost anywhere else in the app. */
        val BASELINE_PACKAGES = listOf(
            "ca-certificates", "curl", "wget", "unzip", "git", "build-essential",
            "golang-go", "python3-pip", "python3-venv", "whois", "dnsutils", "net-tools",
        )
    }
}
