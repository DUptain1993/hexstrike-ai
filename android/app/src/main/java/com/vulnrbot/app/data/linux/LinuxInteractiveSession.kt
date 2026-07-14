package com.vulnrbot.app.data.linux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter

/** A long-lived root shell inside the on-device Ubuntu chroot for the raw Terminal screen. Output
 * is broadcast line-by-line; input is written straight to the shell's stdin. */
class LinuxInteractiveSession(private val chroot: ChrootManager, private val scope: CoroutineScope) {

    private var process: Process? = null
    private var stdin: BufferedWriter? = null

    private val _output = MutableSharedFlow<String>(replay = 500)
    val output: SharedFlow<String> = _output

    val isRunning: Boolean get() = process?.isAlive == true

    fun start() {
        if (isRunning) return
        scope.launch(Dispatchers.IO) {
            if (!chroot.refreshReadiness()) {
                _output.emit("[vulnrbot] Not ready: this device needs root and a chroot at ${chroot.chrootPath()}. Check Settings > Linux environment.")
                return@launch
            }
            val proc = runCatching {
                ProcessBuilder(chroot.interactiveArgv()).redirectErrorStream(true).start()
            }.getOrElse {
                _output.emit("[vulnrbot] Failed to start root shell: ${it.message}")
                return@launch
            }
            process = proc
            stdin = BufferedWriter(OutputStreamWriter(proc.outputStream))

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
        // Best-effort Ctrl+C: the shell has no controlling terminal, so send an ETX byte down the
        // pipe, which most interactive CLIs (sqlmap, python, etc.) treat like a real SIGINT.
        runCatching { stdin?.write(3); stdin?.flush() }
    }

    fun stop() {
        runCatching { stdin?.close() }
        process?.destroy()
        process = null
        stdin = null
    }
}
