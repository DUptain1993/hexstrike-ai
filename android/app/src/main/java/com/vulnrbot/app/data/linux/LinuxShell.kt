package com.vulnrbot.app.data.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class ExecResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
)

/** One-shot command execution inside the on-device Ubuntu chroot (as real root, via
 * [ChrootManager]). Used by tool execution and the environment installer. For an interactive shell
 * see [LinuxInteractiveSession]. */
class LinuxShell(private val chroot: ChrootManager) {

    /** True once root + a usable chroot have been confirmed; refreshes on first use if never
     * checked, so callers that run before Settings has probed still work. */
    suspend fun isReady(): Boolean = chroot.lastReady || chroot.refreshReadiness()

    /**
     * Installs each package as its own `apt-get install` transaction instead of one combined
     * call. apt-get treats a multi-package install as a single atomic transaction, so if any one
     * package's postinst script fails, dpkg aborts the *entire* transaction — taking down every
     * other, otherwise-healthy package bundled into the same call.
     *
     * Also clears any stale dpkg lock / half-configured state left by an earlier attempt before
     * starting. Under real root (unlike proot) the pkill actually reaps the host process holding
     * the lock, and the lock-file removal is authoritative.
     */
    suspend fun installPackagesIndividually(
        packages: List<String>,
        onResult: (pkg: String, result: ExecResult) -> Unit = { _, _ -> },
    ) {
        exec(
            "pkill -9 -f apt-get; pkill -9 -f dpkg; " +
                "rm -f /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock /var/cache/apt/archives/lock; true",
            timeoutMs = 15_000L,
        )
        exec("dpkg --configure -a", timeoutMs = 2 * 60 * 1000L)
        exec("DEBIAN_FRONTEND=noninteractive apt-get $LOCK_TIMEOUT_OPT install -f -y", timeoutMs = 3 * 60 * 1000L)
        for (pkg in packages) {
            val result = exec("DEBIAN_FRONTEND=noninteractive apt-get $LOCK_TIMEOUT_OPT install -y $pkg", timeoutMs = 4 * 60 * 1000L)
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
                output = "The on-device Linux environment isn't ready: this device needs root and a chroot at " +
                    "${chroot.chrootPath()}. Check Settings > Linux environment.",
            )
        }

        val jobId = "vulnrjob_" + UUID.randomUUID().toString().replace("-", "")
        val process = ProcessBuilder(chroot.oneShotArgv(shellLine, jobId))
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
            // Killing the `su` process alone won't reap the chrooted grandchildren (chroot doesn't
            // create a process group), so ask root to kill the job's bash by its JOBID marker plus
            // any apt/dpkg it spawned (the long-running lock-holders) before tearing down su itself.
            chroot.killJob(jobId)
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

        /** Recent apt-get retries on a contended dpkg lock by default instead of failing fast;
         * bounding it keeps a stuck lock from silently eating each call's whole timeout. */
        const val LOCK_TIMEOUT_OPT = "-o DPkg::Lock::Timeout=20"
    }
}
