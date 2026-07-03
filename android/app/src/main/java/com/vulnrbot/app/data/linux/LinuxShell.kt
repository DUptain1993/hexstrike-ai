package com.vulnrbot.app.data.linux

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

    /**
     * Installs each package as its own `apt-get install` transaction instead of one combined
     * call. apt-get treats a multi-package install as a single atomic transaction, so if any one
     * package's postinst script fails (observed in practice: a python3.12/sqlmap dependency
     * leaving dpkg with a "half-configured" package), dpkg aborts the *entire* transaction —
     * taking down every other, otherwise-healthy package bundled into the same call. That's what
     * turns "some tools may not exist in Ubuntu's repos" into "git/go/pip3 are missing so every
     * git/go/pip-based tool fails" when baseline setup installs them all in one shot.
     *
     * Also repairs any half-configured state left over from an earlier failed attempt before
     * starting: dpkg retries (and re-fails on) that same broken package as a precondition for
     * every subsequent transaction until it's cleared, which otherwise poisons every later
     * install — including a completely unrelated tool's — with the same failure.
     */
    suspend fun installPackagesIndividually(
        packages: List<String>,
        onResult: (pkg: String, result: ExecResult) -> Unit = { _, _ -> },
    ) {
        // A previous run's apt-get can be left running as an orphan holding
        // /var/lib/dpkg/lock-frontend forever (see exec()'s timeout handling below for why) —
        // that poisons every apt-get call for the rest of *this* run too, since dpkg won't even
        // attempt an install while the lock is held. Since nothing else in this app talks to apt,
        // any apt-get/dpkg still running at the start of a fresh batch is necessarily a stale
        // orphan, safe to kill before clearing its lock files.
        exec(
            "pkill -9 -f apt-get; pkill -9 -f dpkg; " +
                "rm -f /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock /var/cache/apt/archives/lock; true",
            timeoutMs = 15_000L,
        )
        exec("dpkg --configure -a", timeoutMs = 5 * 60 * 1000L)
        exec("DEBIAN_FRONTEND=noninteractive apt-get install -f -y", timeoutMs = 5 * 60 * 1000L)
        for (pkg in packages) {
            val result = exec("DEBIAN_FRONTEND=noninteractive apt-get install -y $pkg", timeoutMs = 10 * 60 * 1000L)
            onResult(pkg, result)
        }
    }

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
            // proot's --kill-on-exit only tears down everything it started if proot itself gets
            // to run that cleanup - SIGKILLing proot outright (destroyForcibly) kills the
            // supervisor instantly with no chance to do that, orphaning whatever it was
            // ptrace-supervising (e.g. an in-progress apt-get) as a real, independent process
            // that keeps running - and keeps holding locks like /var/lib/dpkg/lock-frontend -
            // indefinitely. Try a graceful SIGTERM first so --kill-on-exit's own cleanup can
            // actually run, and only fall back to SIGKILL if proot doesn't exit on its own.
            process.destroy()
            val exitedGracefully = withTimeoutOrNull(3_000) {
                while (process.isAlive) delay(50)
                true
            }
            if (exitedGracefully == null) {
                process.destroyForcibly()
            }
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
