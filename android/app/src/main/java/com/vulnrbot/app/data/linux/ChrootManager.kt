package com.vulnrbot.app.data.linux

import android.util.Base64
import com.vulnrbot.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Enters an existing on-device Ubuntu chroot as **real root**, via `su`.
 *
 * This replaces the old proot approach entirely. proot faked uid 0 with a ptrace supervisor and
 * couldn't open raw sockets, orphaned children when killed, and needed fragile native-lib
 * packaging. A real root + real chroot has none of those problems: real process semantics, real
 * raw sockets, real apt. The tradeoff is that the device MUST be rooted and MUST already have a
 * chroot set up (this app does not build one) — both are surfaced as explicit states rather than
 * silently degrading.
 *
 * The mount logic mirrors the user's own known-good `start_ubuntu.sh`, with one deliberate
 * difference: mounts are **persistent and idempotent** (guarded by `grep -q /proc/mounts`) and are
 * never unmounted per-command. That script unmounts on exit because it runs a single interactive
 * session; we run many short commands, so tearing the mounts down between each would be both slow
 * and racy.
 */
class ChrootManager(private val settingsRepository: SettingsRepository) {

    /** Cached result of the last root+chroot probe, so hot-path callers ([LinuxShell.isReady])
     * don't shell out to `su` on every command. Refreshed by [refreshReadiness]. */
    @Volatile
    var lastReady: Boolean = false
        private set

    fun chrootPath(): String = settingsRepository.current.chrootPath.trim().ifBlank { DEFAULT_CHROOT_PATH }

    /** Re-probes root + chroot and updates [lastReady]. */
    suspend fun refreshReadiness(): Boolean {
        val ready = checkRoot() && checkChroot()
        lastReady = ready
        return ready
    }

    /**
     * Boot script shared by every invocation: (best-effort) fix /data's nosuid mount, put the
     * standard bind mounts in place if they aren't already, and refresh resolv.conf. Ends with the
     * caller-supplied [tail], which is the actual `exec chroot …` line.
     */
    private fun bootScript(tail: String): String {
        val ch = shSingleQuote(chrootPath())
        return """
            CH=$ch
            busybox mount -o remount,dev,suid /data 2>/dev/null || mount -o remount,dev,suid /data 2>/dev/null || true
            grep -q " ${'$'}CH/dev " /proc/mounts || mount --bind /dev "${'$'}CH/dev"
            grep -q " ${'$'}CH/dev/pts " /proc/mounts || mount -t devpts devpts "${'$'}CH/dev/pts"
            grep -q " ${'$'}CH/proc " /proc/mounts || mount --bind /proc "${'$'}CH/proc"
            grep -q " ${'$'}CH/sys " /proc/mounts || mount --bind /sys "${'$'}CH/sys"
            grep -q " ${'$'}CH/dev/shm " /proc/mounts || { mkdir -p "${'$'}CH/dev/shm"; mount -t tmpfs -o size=256M tmpfs "${'$'}CH/dev/shm"; }
            grep -q " ${'$'}CH/sdcard " /proc/mounts || { mkdir -p "${'$'}CH/sdcard"; mount --bind /sdcard "${'$'}CH/sdcard"; }
            printf 'nameserver 8.8.8.8\nnameserver 1.1.1.1\n' > "${'$'}CH/etc/resolv.conf" 2>/dev/null || true
            $tail
        """.trimIndent()
    }

    /**
     * argv for a one-shot command run inside the chroot. The real command is base64-encoded so the
     * boot script stays 100% static text — the only quoting boundary that matters (the tool
     * command, which may contain arbitrary quotes/pipes/redirects) is carried across as an opaque,
     * shell-safe blob and decoded at runtime. [jobId] is appended as a bash comment so the command
     * shows up in the process cmdline and can be killed later with `pkill -f <jobId>`.
     */
    fun oneShotArgv(command: String, jobId: String): List<String> {
        val encoded = Base64.encodeToString(command.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val tail = buildString {
            append("CMD=\"\$(printf %s '").append(encoded).append("' | base64 -d)\"\n")
            append("exec chroot \"\$CH\" /usr/bin/env -i ")
            append(ENV_ASSIGNMENTS)
            append(" /bin/bash --login -c \"\$CMD  #").append(jobId).append("\"")
        }
        return listOf("su", "-c", bootScript(tail))
    }

    /** argv for the long-lived interactive shell behind the Terminal tab. */
    fun interactiveArgv(): List<String> {
        val tail = "exec chroot \"\$CH\" /usr/bin/env -i $ENV_ASSIGNMENTS /bin/bash --login -i"
        return listOf("su", "-c", bootScript(tail))
    }

    /** Best-effort teardown of a runaway one-shot: its bash (matched by the JOBID comment) plus any
     * apt/dpkg it spawned — those are the long-running, lock-holding culprits. */
    suspend fun killJob(jobId: String) {
        runSuRaw("pkill -9 -f ${shSingleQuote(jobId)}; pkill -9 -f apt-get; pkill -9 -f dpkg; true", timeoutMs = 15_000L)
    }

    suspend fun checkRoot(): Boolean {
        val result = runSuRaw("id", timeoutMs = 8_000L)
        return result.exitCode == 0 && result.output.contains("uid=0")
    }

    suspend fun checkChroot(): Boolean {
        val ch = shSingleQuote(chrootPath())
        val result = runSuRaw("test -x $ch/bin/bash && echo CHROOT_OK", timeoutMs = 8_000L)
        return result.exitCode == 0 && result.output.contains("CHROOT_OK")
    }

    /** Human-readable output for the Settings "Test root & chroot" button — shows the raw `id`
     * result and the chroot probe so the user can see exactly what's wrong without a screenshot. */
    suspend fun testRootAndChroot(): String {
        val id = runSuRaw("id", timeoutMs = 8_000L)
        val rooted = id.exitCode == 0 && id.output.contains("uid=0")
        val path = chrootPath()
        val builder = StringBuilder()
        builder.appendLine("Root: ${if (rooted) "GRANTED" else "NOT AVAILABLE"}")
        builder.appendLine("  su -c id → ${id.output.trim().ifBlank { "(no output; exit ${id.exitCode}${if (id.timedOut) ", timed out" else ""})" }}")
        if (rooted) {
            val probe = runSuRaw("test -x ${shSingleQuote(path)}/bin/bash && echo CHROOT_OK || echo NO_BASH", timeoutMs = 8_000L)
            val ok = probe.output.contains("CHROOT_OK")
            builder.appendLine("Chroot ($path): ${if (ok) "FOUND" else "NOT FOUND"}")
            builder.append("  test -x $path/bin/bash → ${probe.output.trim().ifBlank { "(no output; exit ${probe.exitCode})" }}")
        } else {
            builder.append("Chroot: not checked (need root first). Grant this app root in your superuser manager, then retry.")
        }
        return builder.toString()
    }

    /**
     * Runs a command in Android's root shell directly (NOT inside the chroot) and captures merged
     * stdout/stderr. Used only for the short detection/diagnostic/kill commands; chrooted tool
     * execution goes through [LinuxShell.exec], which needs streaming and a much longer timeout.
     */
    private suspend fun runSuRaw(script: String, timeoutMs: Long): ExecResult = withContext(Dispatchers.IO) {
        val process = runCatching {
            ProcessBuilder(listOf("su", "-c", script)).redirectErrorStream(true).start()
        }.getOrElse {
            return@withContext ExecResult(exitCode = -1, output = "su not available: ${it.message}")
        }
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line -> synchronized(output) { output.appendLine(line) } }
            }
        }.apply { isDaemon = true; start() }

        val completed = withTimeoutOrNull(timeoutMs) {
            while (process.isAlive) delay(50)
            true
        }
        if (completed == null) {
            process.destroy()
            withTimeoutOrNull(2_000) { while (process.isAlive) delay(50); true } ?: process.destroyForcibly()
            reader.join(1_000)
            return@withContext ExecResult(exitCode = -1, output = synchronized(output) { output.toString() }, timedOut = true)
        }
        reader.join(1_500)
        ExecResult(exitCode = process.exitValue(), output = synchronized(output) { output.toString() })
    }

    companion object {
        const val DEFAULT_CHROOT_PATH = "/data/local/chroot/ubuntu"

        /** Environment handed to the chrooted bash. Kept identical across one-shot and interactive
         * so tools behave the same whether launched by the agent or typed in the Terminal. */
        private const val ENV_ASSIGNMENTS =
            "HOME=/root USER=root TERM=xterm-256color LANG=C.UTF-8 " +
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/go/bin:/root/.local/bin"

        /** POSIX single-quote escaping so a path/JOBID can't break out of the boot script. */
        private fun shSingleQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
