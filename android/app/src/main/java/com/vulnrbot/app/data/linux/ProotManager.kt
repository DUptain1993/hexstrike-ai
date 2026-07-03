package com.vulnrbot.app.data.linux

import java.io.File

/**
 * Builds the proot invocation used to enter the downloaded Ubuntu rootfs without root.
 * Flag choices mirror termux's proot-distro launch script, which is the reference
 * implementation for running proot reliably on stock Android:
 *  --link2symlink  redirect hardlink() to symlink() since app-private storage often can't
 *                  represent hardlinks the way a real Linux filesystem can.
 *  --kill-on-exit  tear down every process in the rootfs when the top-level command exits,
 *                  so a killed scan doesn't leave orphaned nmap/sqlmap processes behind.
 *  -0              fake root (uid/gid 0) inside the rootfs; required by apt and most installers.
 */
class ProotManager(private val paths: LinuxEnvironmentPaths) {

    fun isAvailable(): Boolean =
        paths.prootBinary.exists() && paths.rootfsDir.exists() && paths.markerFile.exists()

    fun shellCommand(shellLine: String): List<String> = listOf("/bin/sh", "-c", shellLine)

    fun buildProcessBuilder(command: List<String>, workingDir: String = "/root"): ProcessBuilder {
        val args = buildList {
            add(paths.prootBinary.absolutePath)
            add("--link2symlink")
            add("--kill-on-exit")
            add("-0")
            add("-r"); add(paths.rootfsDir.absolutePath)
            add("-b"); add("/dev")
            add("-b"); add("/proc")
            add("-b"); add("/sys")
            add("-w"); add(workingDir)
            add("/usr/bin/env")
            add("-i")
            addAll(ENV_VARS.map { (key, value) -> "$key=$value" })
            addAll(command)
        }

        val processBuilder = ProcessBuilder(args)
        val nativeLibDir: File? = paths.prootBinary.parentFile
        processBuilder.environment()["PROOT_TMP_DIR"] = paths.prootTmpDir.absolutePath
        processBuilder.environment()["PROOT_L2S_DIR"] = paths.prootLink2SymlinkDir.absolutePath
        // proot is a dynamically-linked ELF (needs libtalloc.so/libandroid-shmem.so, both staged
        // alongside it in the same jniLibs-extracted directory) and shells out to separate loader
        // helper binaries rather than exec'ing the target directly. All three live next to
        // libproot.so itself — see scripts/fetch-proot.sh and native/README.md.
        if (nativeLibDir != null) {
            processBuilder.environment()["LD_LIBRARY_PATH"] = nativeLibDir.absolutePath
            File(nativeLibDir, "libproot_loader.so").takeIf { it.exists() }?.let {
                processBuilder.environment()["PROOT_LOADER"] = it.absolutePath
            }
            File(nativeLibDir, "libproot_loader32.so").takeIf { it.exists() }?.let {
                processBuilder.environment()["PROOT_LOADER_32"] = it.absolutePath
            }
        }
        processBuilder.redirectErrorStream(false)
        return processBuilder
    }

    companion object {
        val ENV_VARS = linkedMapOf(
            "HOME" to "/root",
            "USER" to "root",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/go/bin",
        )
    }
}
