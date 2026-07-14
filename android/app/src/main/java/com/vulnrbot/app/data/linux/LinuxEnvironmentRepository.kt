package com.vulnrbot.app.data.linux

import com.vulnrbot.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Top-level facade over the Linux subsystem. The environment is an existing on-device Ubuntu
 * **chroot** entered as real root — this class detects root + the chroot, runs one-time baseline
 * setup inside it, and exposes the [LinuxShell] used for tool execution. One instance lives for the
 * app's lifetime (see VulnrBotApplication). */
class LinuxEnvironmentRepository(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    val chroot = ChrootManager(settingsRepository)
    val shell = LinuxShell(chroot)

    private val _state = MutableStateFlow<LinuxEnvironmentState>(LinuxEnvironmentState.CheckingRoot)
    val state: StateFlow<LinuxEnvironmentState> = _state.asStateFlow()

    init {
        refreshState()
    }

    /** Re-probes root + chroot and updates [state]. Cheap enough to call whenever the user opens
     * Settings or edits the chroot path. */
    fun refreshState() {
        scope.launch {
            _state.value = LinuxEnvironmentState.CheckingRoot
            _state.value = computeState()
        }
    }

    private suspend fun computeState(): LinuxEnvironmentState {
        if (!chroot.checkRoot()) return LinuxEnvironmentState.NeedsRoot
        if (!chroot.checkChroot()) return LinuxEnvironmentState.ChrootNotFound(chroot.chrootPath())
        chroot.refreshReadiness()
        return LinuxEnvironmentState.Ready
    }

    /** Runs one-time baseline setup (apt update + core packages: git, go, pip3, …) inside the
     * existing chroot, so the tool catalog's install commands have their prerequisites. Safe to
     * re-run — apt is idempotent. */
    fun install() {
        scope.launch {
            if (!chroot.refreshReadiness()) {
                _state.value = if (!chroot.checkRoot()) {
                    LinuxEnvironmentState.NeedsRoot
                } else {
                    LinuxEnvironmentState.ChrootNotFound(chroot.chrootPath())
                }
                return@launch
            }
            try {
                _state.value = LinuxEnvironmentState.Preparing
                runBaselineSetup()
                _state.value = LinuxEnvironmentState.Ready
            } catch (e: Exception) {
                _state.value = LinuxEnvironmentState.Error(e.message ?: "Baseline setup failed")
            }
        }
    }

    /** Raw diagnostic for the Settings "Test root & chroot" button. */
    suspend fun testRootAndChroot(): String = chroot.testRootAndChroot()

    private suspend fun runBaselineSetup() {
        shell.exec("apt-get update -y ${LinuxShell.LOCK_TIMEOUT_OPT}", timeoutMs = 3 * 60 * 1000L)
        shell.installPackagesIndividually(BASELINE_PACKAGES)
    }

    companion object {
        /** Installed one at a time — see [LinuxShell.installPackagesIndividually]. git/go/pip3 are
         * load-bearing for most of the tool catalog's install commands. */
        val BASELINE_PACKAGES = listOf(
            "ca-certificates", "curl", "wget", "unzip", "git", "build-essential",
            "golang-go", "python3-pip", "python3-venv", "whois", "dnsutils", "net-tools",
        )
    }
}
