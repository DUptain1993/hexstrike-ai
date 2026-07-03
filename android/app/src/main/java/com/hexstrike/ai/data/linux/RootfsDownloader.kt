package com.hexstrike.ai.data.linux

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/**
 * Fetches Ubuntu's official minimal root filesystem tarball and extracts it into app-private
 * storage. Same source used by proot-distro and similar tools; nothing is bundled in the APK.
 */
class RootfsDownloader(private val paths: LinuxEnvironmentPaths) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    fun ubuntuArchOrNull(): String? = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "armhf"
        "x86_64" -> "amd64"
        else -> null
    }

    sealed interface Progress {
        data class Downloading(val bytesRead: Long, val totalBytes: Long) : Progress
        data object Extracting : Progress
        data object Done : Progress
    }

    fun install(release: String = DEFAULT_RELEASE): Flow<Progress> = callbackFlow {
        paths.ensureDirs()
        val arch = ubuntuArchOrNull() ?: throw UnsupportedOperationException(
            "No Ubuntu base rootfs published for this device's CPU architecture (${Build.SUPPORTED_ABIS.joinToString()})",
        )
        val url = resolveRootfsUrl(release, arch)
        val tarball = File(paths.downloadsDir, "ubuntu-base-$release.tar.gz")

        downloadTo(url, tarball) { read, total -> trySend(Progress.Downloading(read, total)) }

        trySend(Progress.Extracting)
        extractTo(tarball, paths.rootfsDir)
        tarball.delete()

        writeResolvConf()
        paths.markerFile.writeText(release)

        trySend(Progress.Done)
        close()
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    /**
     * Ubuntu's cdimage only ever keeps the most recent point releases' tarballs in a given LTS's
     * `release/` directory — `ubuntu-base-24.04-base-arm64.tar.gz` (no point suffix) has never
     * been the real filename, and even a pinned `24.04.1` eventually 404s once Canonical publishes
     * `24.04.2` and prunes the old one. There's no stable "latest" alias for ubuntu-base builds, so
     * this fetches the directory listing and picks the highest point release actually present for
     * this release/arch instead of guessing a filename.
     */
    private fun resolveRootfsUrl(release: String, arch: String): String {
        val listingUrl = "https://cdimage.ubuntu.com/ubuntu-base/releases/$release/release/"
        val request = Request.Builder().url(listingUrl).build()
        val html = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("Failed to list available Ubuntu $release rootfs builds: HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
        val pattern = Regex("""ubuntu-base-${Regex.escape(release)}(?:\.(\d+))?-base-${Regex.escape(arch)}\.tar\.gz""")
        val filename = pattern.findAll(html)
            .map { it.value to (it.groupValues[1].toIntOrNull() ?: 0) }
            .distinct()
            .maxByOrNull { (_, point) -> point }
            ?.first
            ?: throw java.io.IOException(
                "No published Ubuntu $release base rootfs found for $arch at $listingUrl — Canonical may have moved or renamed it.",
            )
        return "$listingUrl$filename"
    }

    private fun downloadTo(url: String, destination: File, onProgress: (Long, Long) -> Unit) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("Failed to download rootfs: HTTP ${response.code}")
            }
            val body = response.body ?: throw java.io.IOException("Empty rootfs download response")
            val total = body.contentLength()
            var readSoFar = 0L
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readSoFar += read
                        onProgress(readSoFar, total)
                    }
                }
            }
        }
    }

    private fun extractTo(tarball: File, destinationDir: File) {
        destinationDir.mkdirs()
        GzipCompressorInputStream(tarball.inputStream().buffered()).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    extractEntry(tar, entry, destinationDir)
                    entry = tar.nextTarEntry
                }
            }
        }
    }

    private fun extractEntry(tar: TarArchiveInputStream, entry: TarArchiveEntry, destinationDir: File) {
        val outFile = File(destinationDir, entry.name)
        val normalizedDest = destinationDir.canonicalPath
        if (!outFile.canonicalPath.startsWith(normalizedDest)) {
            // Refuse to extract outside the target directory (zip-slip / tar-slip protection).
            return
        }
        when {
            entry.isDirectory -> outFile.mkdirs()
            entry.isSymbolicLink -> {
                outFile.parentFile?.mkdirs()
                outFile.delete()
                runCatching { Files.createSymbolicLink(outFile.toPath(), Paths.get(entry.linkName)) }
            }
            else -> {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { out -> tar.copyTo(out) }
                applyPermissions(outFile, entry.mode)
            }
        }
    }

    private fun applyPermissions(file: File, mode: Int) {
        val permissions = mutableSetOf<PosixFilePermission>()
        if (mode and 0b001_000_000 != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE)
        if (mode and 0b010_000_000 != 0) permissions.add(PosixFilePermission.OWNER_WRITE)
        if (mode and 0b100_000_000 != 0) permissions.add(PosixFilePermission.OWNER_READ)
        // Always keep files at least owner-read/write/execute so proot's fake-root layer can
        // manage the rest; the host filesystem can't represent full multi-user permissions anyway.
        permissions.add(PosixFilePermission.OWNER_READ)
        permissions.add(PosixFilePermission.OWNER_WRITE)
        runCatching { Files.setPosixFilePermissions(file.toPath(), permissions) }
    }

    private fun writeResolvConf() {
        val resolvConf = File(paths.rootfsDir, "etc/resolv.conf")
        resolvConf.parentFile?.mkdirs()
        resolvConf.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
    }

    companion object {
        const val DEFAULT_RELEASE = "24.04"
    }
}
