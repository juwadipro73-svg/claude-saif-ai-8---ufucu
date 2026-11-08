package com.saif.ai.builder.net

import org.json.JSONObject

data class AppRecord(
    val appName: String,
    val driveFileId: String?,
    val downloadUrl: String?,
    val status: String,
)

/**
 * Workflow Firebase Realtime DB me record likhta hai:
 *   PUT {DB_URL}/apps/{app_key}.json
 *   { appName, driveFileId, downloadUrl, status, createdAt }
 */
object FirebaseClient {

    fun fetchAppRecord(dbUrl: String, appKey: String): AppRecord? {
        if (dbUrl.isBlank()) return null
        val url = "${dbUrl.trimEnd('/')}/apps/$appKey.json"
        val response = Http.request(url)
        if (!response.isSuccessful) return null

        val body = response.body.trim()
        if (body.isEmpty() || body == "null") return null

        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return AppRecord(
            appName = json.optString("appName"),
            driveFileId = json.optString("driveFileId").takeIf { it.isNotBlank() && it != "null" },
            downloadUrl = json.optString("downloadUrl").takeIf { it.isNotBlank() && it != "null" },
            status = json.optString("status")
        )
    }

    /** Prompt ko Firebase key me badalta hai — workflow ke `sed` jaisa hi. */
    fun appKeyFor(appName: String): String =
        appName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
}
