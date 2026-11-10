/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.data.local

import android.content.Context
import android.os.Build
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Installs Google's Linux ARM64 CLI in the Ubuntu rootfs, not the desktop GUI. */
object AntigravityCliInstaller {
    private const val MANIFEST = "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_arm64.json"
    private const val MAX_BYTES = 512L * 1024 * 1024
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS).build()

    suspend fun ensureInstalled(context: Context) = mutex.withLock {
        if (!Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            error("Antigravity automatic installation requires an ARM64 device.")
        }
        val dest = File(LocalEnvironmentManager.getUbuntuRootDir(context), "root/.local/bin/agy")
        if (dest.isFile && dest.length() > 1024 * 1024 && dest.canExecute()) return@withLock
        dest.parentFile?.mkdirs()
        val manifest = client.newCall(Request.Builder().url(MANIFEST).build()).execute().use { response ->
            check(response.isSuccessful) { "Antigravity manifest download failed (HTTP ${response.code})." }
            JSONObject(response.body?.string() ?: error("Empty Antigravity manifest."))
        }
        val url = manifest.getString("url")
        val parsed = android.net.Uri.parse(url)
        require(parsed.scheme == "https" && parsed.host == "storage.googleapis.com" &&
            parsed.path.orEmpty().startsWith("/antigravity-public/antigravity-cli/") &&
            parsed.path.orEmpty().endsWith(".tar.gz")) { "Unexpected Antigravity download URL." }
        val expectedHash = manifest.getString("sha512").lowercase()
        require(expectedHash.matches(Regex("[0-9a-f]{128}"))) { "Invalid Antigravity checksum." }
        val archive = File.createTempFile("agy-", ".tar.gz", context.cacheDir)
        val staged = File(dest.parentFile, ".agy-install.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-512")
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Antigravity download failed (HTTP ${response.code})." }
                val input = response.body?.byteStream() ?: error("Empty Antigravity download.")
                archive.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_BYTES) { "Antigravity download exceeds the size limit." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(actual == expectedHash) { "Antigravity checksum mismatch; installation stopped." }
            var found = false
            TarArchiveInputStream(GZIPInputStream(archive.inputStream())).use { tar ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = tar.nextTarEntry ?: break
                    if (entry.name.removePrefix("./") != "antigravity") continue
                    check(!found && entry.isFile && entry.size in 1..MAX_BYTES) { "Invalid Antigravity executable in archive." }
                    staged.outputStream().use { output -> tar.copyTo(output) }
                    found = true
                }
            }
            check(found && staged.length() > 1024 * 1024) { "Antigravity executable is missing or incomplete." }
            check(staged.setExecutable(true, true)) { "Cannot make Antigravity executable." }
            check(staged.renameTo(dest)) { "Cannot install Antigravity executable." }
        } finally {
            archive.delete()
            staged.delete()
        }
    }
}
