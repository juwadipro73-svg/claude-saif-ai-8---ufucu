package com.example.gemini.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.gemini.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

private const val TAG = "AppUpdateManager"

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val currentVersionCode: Int,
    val currentVersionName: String,
    val packageName: String,
    val downloadUrl: String,
    val changelog: List<String>,
    val isCritical: Boolean = false
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class AppUpdateManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder().followRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()


    /** Check the owner's website, never open or infer an update from the private repository. */
    suspend fun checkForUpdates(forceCheck: Boolean = false): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val website = BuildConfig.SAIF_DOWNLOAD_PORTAL_URL.trimEnd('/')
            val base = runCatching { URI(website) }.getOrNull()
            if (base == null || base.scheme != "https" || base.host.isNullOrBlank() || base.userInfo != null ||
                base.port != -1 || base.rawQuery != null || base.rawFragment != null) {
                return@withContext Result.failure(IllegalStateException(
                    "Updates are not configured on the SAIF AI website yet."))
            }
            val versionJsonUrl = "$website/version.json"
            val request = Request.Builder()
                .url(versionJsonUrl)
                .header("User-Agent", "SAIF-AI-Updater/${BuildConfig.VERSION_NAME}")
                .header("Cache-Control", "no-cache")
                .build()

            val rawJson = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = if (response.code == 404)
                        "The SAIF AI website has not published an update feed yet."
                    else "Website update check failed (HTTP ${response.code})."
                    return@withContext Result.failure(Exception(message))
                }
                response.body?.string()?.takeIf { it.length <= 32_768 }
                    ?: return@withContext Result.failure(Exception("Invalid website update feed"))
            }

            val json = JSONObject(rawJson)
            val remoteVersionCode = json.optInt("version_code", 0)
            val remoteVersionName = json.optString("version_name", "1.0.0")
            val minSupportedCode = json.optInt("min_supported_version_code", 0)
            val changelog = when (val raw = json.opt("changelog")) {
                is org.json.JSONArray -> (0 until raw.length()).map { raw.optString(it) }
                is String -> listOf(raw)
                else -> listOf("General improvements and bug fixes.")
            }
            val isCritical = json.optBoolean("is_critical", false) || (BuildConfig.VERSION_CODE < minSupportedCode)

            val currentPkg = context.packageName
            if (remoteVersionCode <= 0 || json.optString("package_name") != currentPkg)
                return@withContext Result.failure(Exception("Website update feed has an invalid app version or package."))
            val downloadUrl = json.optString("download_url")
            if (remoteVersionCode > BuildConfig.VERSION_CODE) {
                if (!isWebsiteApk(downloadUrl)) return@withContext Result.failure(Exception(
                    "The update feed needs a signed APK URL on the SAIF AI website."))
                // Never offer an update until the website actually serves the APK.
                val probeRequest = Request.Builder()
                    .url(downloadUrl)
                    .head()
                    .header("User-Agent", "SAIF-AI-Updater/${BuildConfig.VERSION_NAME}")
                    .build()

                val isAvailable = try {
                    httpClient.newCall(probeRequest).execute().use { probeResponse ->
                        probeResponse.isSuccessful
                    }
                } catch (_: Exception) {
                    false
                }

                if (!isAvailable) {
                    return@withContext Result.failure(Exception("The website update APK is not available yet."))
                }

                Result.success(
                    AppUpdateInfo(
                        versionCode = remoteVersionCode,
                        versionName = remoteVersionName,
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        currentVersionName = BuildConfig.VERSION_NAME,
                        packageName = currentPkg,
                        downloadUrl = downloadUrl,
                        changelog = changelog,
                        isCritical = isCritical
                    )
                )
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun isWebsiteApk(url: String): Boolean = runCatching {
        val site = URI(BuildConfig.SAIF_DOWNLOAD_PORTAL_URL.trimEnd('/'))
        val apk = URI(url)
        apk.scheme == "https" && apk.host == site.host && apk.port == site.port &&
            apk.userInfo == null && apk.rawQuery == null && apk.rawFragment == null &&
            apk.path.endsWith(".apk", ignoreCase = true) &&
            apk.path.split('/').none { it == "." || it == ".." }
    }.getOrDefault(false)

    /** Download the signed update APK only from the configured website (not a repository). */
    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            require(isWebsiteApk(downloadUrl)) { "Update APK must be hosted on the SAIF AI website" }
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "SAIF-AI-Updater/${BuildConfig.VERSION_NAME}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty download body"))
            val totalBytes = body.contentLength()

            val targetDir = context.externalCacheDir ?: context.cacheDir
            val targetFile = File(targetDir, "saif_ai_update_${System.currentTimeMillis()}.apk")

            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var downloadedBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        withContext(Dispatchers.Main) {
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download APK error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Launches the system Package Installer using FileProvider.
     */
    fun installApk(apkFile: File): Boolean {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Android 8.0+ Unknown sources permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                    return false
                }
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            false
        }
    }
}
