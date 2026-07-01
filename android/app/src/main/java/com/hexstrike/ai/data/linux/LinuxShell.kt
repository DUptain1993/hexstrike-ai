package com.hexstrike.ai.data.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ExecResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
)

/** One-shot command execution inside the proot Ubuntu environment, used by tool execution and
 * the environment installer. For an interactive shell see [LinuxInteractiveSession]. */
class LinuxShell(paths: LinuxEnvironmentPaths) {

    private val prootManager = ProotManager(paths)

    fun isReady(): Boolean = prootManager.isAvailable()

    suspend fun exec(
        shellLine: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onOutputLine: (String) -> Unit = {},
    ): ExecResult = withContext(Dispatchers.IO) {
        if (!isReady()) {
            return@withContext ExecResult(
                exitCode = -1,
                output = "Linux environment is not installed yet. Set it up from Settings first.",
            )
        }

        val process = prootManager.buildProcessBuilder(prootManager.shellCommand(shellLine))
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        val readerThread = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(output) { output.appendLine(line) }
                    onOutputLine(line)
                }
            }
        }.apply { isDaemon = true; start() }

        val completed = withTimeoutOrNull(timeoutMs) {
            while (process.isAlive) delay(75)
            true
        }

        if (completed == null) {
            process.destroyForcibly()
            readerThread.join(1_000)
            return@withContext ExecResult(exitCode = -1, output = synchronized(output) { output.toString() }, timedOut = true)
        }

        readerThread.join(2_000)
        ExecResult(exitCode = process.exitValue(), output = synchronized(output) { output.toString() })
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
