package com.vulnrbot.app.data.linux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter

/** A long-lived shell inside the proot Ubuntu environment for the raw Terminal screen. Output is
 * broadcast line-by-line; input is written straight to the shell's stdin. */
class LinuxInteractiveSession(private val paths: LinuxEnvironmentPaths, private val scope: CoroutineScope) {

    private val prootManager = ProotManager(paths)
    private var process: Process? = null
    private var stdin: BufferedWriter? = null

    private val _output = MutableSharedFlow<String>(replay = 500)
    val output: SharedFlow<String> = _output

    val isRunning: Boolean get() = process?.isAlive == true

    fun start() {
        if (isRunning) return
        if (!prootManager.isAvailable()) {
            scope.launch { _output.emit("[vulnrbot] Linux environment is not installed yet.") }
            return
        }
        val shellPath = "/bin/bash"
        val proc = prootManager.buildProcessBuilder(listOf(shellPath, "-i"))
            .redirectErrorStream(true)
            .start()
        process = proc
        stdin = BufferedWriter(OutputStreamWriter(proc.outputStream))

        scope.launch(Dispatchers.IO) {
            runCatching {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    scope.launch { _output.emit(line) }
                }
            }
            scope.launch { _output.emit("[vulnrbot] session ended") }
        }
    }

    fun send(line: String) {
        val writer = stdin ?: return
        runCatching {
            writer.write(line)
            writer.newLine()
            writer.flush()
        }
    }

    fun sendSignalInterrupt() {
        // Best-effort Ctrl+C: proot processes don't expose a Java-level signal API, so we send an
        // ETX byte down the pty-less pipe, which most interactive CLIs (sqlmap, python, etc.) treat
        // the same as a real SIGINT delivered by a terminal driver.
        runCatching { stdin?.write(3); stdin?.flush() }
    }

    fun stop() {
        runCatching { stdin?.close() }
        process?.destroy()
        process = null
        stdin = null
    }
}
