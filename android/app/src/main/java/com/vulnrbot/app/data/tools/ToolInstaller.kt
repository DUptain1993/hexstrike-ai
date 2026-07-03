package com.vulnrbot.app.data.tools

import com.vulnrbot.app.data.linux.LinuxEnvironmentRepository
import com.vulnrbot.app.data.linux.LinuxShell
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface InstallProgress {
    data class Installing(val toolId: String, val index: Int, val total: Int) : InstallProgress
    data class ToolResult(val toolId: String, val success: Boolean, val message: String) : InstallProgress
    data object Done : InstallProgress
}

/** Installs security tools into the proot Ubuntu environment. Failures for one tool don't stop
 * the rest — Kali-adjacent tools aren't all guaranteed to exist in plain Ubuntu's repositories,
 * so partial success is the expected common case; the Terminal screen lets a user finish any tool
 * manually afterward. */
class ToolInstaller(private val shell: LinuxShell) {

    fun installTools(toolIds: Collection<String>): Flow<InstallProgress> = flow {
        val tools = toolIds.mapNotNull { SecurityToolRegistry.find(it) }.distinctBy { it.id }

        // Repairs environments that already exist (not just fresh ones) — see
        // LinuxShell.installPackagesIndividually. An environment set up before that fix landed
        // can be permanently missing git/go/pip3 with no way to fix it short of wiping and
        // re-downloading the whole rootfs; re-running this here on every "Install"/"Reinstall
        // all" tap fixes it in place instead.
        emit(InstallProgress.Installing("system packages (git, go, pip3, ...)", 0, tools.size))
        shell.exec("apt-get update -y", timeoutMs = 10 * 60 * 1000L)
        shell.installPackagesIndividually(LinuxEnvironmentRepository.BASELINE_PACKAGES)

        tools.forEachIndexed { index, tool ->
            emit(InstallProgress.Installing(tool.id, index + 1, tools.size))
            val command = tool.install.toShellCommand()
            if (command.isBlank()) {
                emit(InstallProgress.ToolResult(tool.id, true, "no install step needed"))
                return@forEachIndexed
            }
            val result = shell.exec(command, timeoutMs = 10 * 60 * 1000L)
            emit(
                InstallProgress.ToolResult(
                    toolId = tool.id,
                    success = result.exitCode == 0,
                    // Keep enough to include the actual "dpkg: error processing package ..." /
                    // "E: Unable to locate package ..." line, not just the generic boilerplate
                    // ("Errors were encountered while processing...") that always sits at the
                    // very end regardless of the real cause.
                    message = if (result.exitCode == 0) "installed" else result.output.takeLast(1500),
                ),
            )
        }
        emit(InstallProgress.Done)
    }
}
