package com.hexstrike.ai.data.linux

import android.content.Context
import java.io.File

/** Central place for every path the Linux subsystem touches, all inside app-private storage
 * (no external storage / MANAGE_EXTERNAL_STORAGE permission needed). */
class LinuxEnvironmentPaths(context: Context) {
    private val appContext = context.applicationContext

    val rootfsDir: File = File(appContext.filesDir, "rootfs")
    val downloadsDir: File = File(appContext.cacheDir, "linux-downloads")
    val prootTmpDir: File = File(appContext.cacheDir, "proot-tmp")
    val prootLink2SymlinkDir: File = File(appContext.cacheDir, "proot-l2s")

    /** proot is shipped as jniLibs/<abi>/libproot.so so PackageManager extracts it with the
     * execute bit set (see native/README.md); it is never dlopen'd as an actual library. */
    val prootBinary: File get() = File(appContext.applicationInfo.nativeLibraryDir, "libproot.so")

    val markerFile: File get() = File(rootfsDir, ".hexstrike-rootfs-ready")

    fun ensureDirs() {
        rootfsDir.mkdirs()
        downloadsDir.mkdirs()
        prootTmpDir.mkdirs()
        prootLink2SymlinkDir.mkdirs()
    }
}
