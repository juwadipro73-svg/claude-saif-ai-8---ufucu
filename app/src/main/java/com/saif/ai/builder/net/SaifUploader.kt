package com.saif.ai.builder.net

import com.saif.ai.builder.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * APK ko apne "Saif" backend (SAIF_UPLOAD_URL) par upload karta hai.
 * Ye wahi step hai jo aapke snippet me `uploadToSaif(builtApkFile)` tha.
 *
 * Expected server: multipart POST (field name `file`) -> JSON { "url": "..." }
 * ya { "downloadUrl": "..." }. Blank URL ho to null (skip).
 */
object SaifUploader {

    suspend fun uploadToSaif(apkFile: File, appName: String): String? = withContext(Dispatchers.IO) {
        val endpoint = AppConfig.saifUploadUrl
        if (endpoint.isBlank()) return@withContext null
        if (!apkFile.exists() || apkFile.length() == 0L) return@withContext null

        val headers = buildMap {
            if (AppConfig.saifApiKey.isNotBlank()) put("Authorization", "Bearer ${AppConfig.saifApiKey}")
        }

        val response = Http.uploadMultipart(
            url = endpoint,
            file = apkFile,
            fileFieldName = "file",
            fields = mapOf("appName" to appName),
            headers = headers
        )
        if (!response.isSuccessful) return@withContext null

        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return@withContext null
        listOf("url", "downloadUrl", "link")
            .map { json.optString(it) }
            .firstOrNull { it.isNotBlank() }
    }
}
