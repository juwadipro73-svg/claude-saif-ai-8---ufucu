package com.saif.ai.builder.data

import com.saif.ai.builder.AppConfig
import com.saif.ai.builder.net.AppRecord
import com.saif.ai.builder.net.FirebaseClient
import com.saif.ai.builder.net.GitHubClient
import com.saif.ai.builder.net.Http
import com.saif.ai.builder.net.SaifUploader
import com.saif.ai.builder.net.WorkflowRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class BuildResult(
    val appName: String,
    val appKey: String,
    val downloadUrl: String,
    val runUrl: String,
)

/**
 * Poora pipeline:
 *  1. GitHub Actions workflow dispatch
 *  2. Run complete hone tak poll
 *  3. Firebase se download link uthana
 *  4. (optional) APK ko Saif server par upload karke apna link banana
 */
class BuildRepository(
    private val settings: SettingsStore,
    private val cacheDir: File,
) {

    suspend fun buildAndPublish(prompt: String, onStep: (String) -> Unit): BuildResult =
        withContext(Dispatchers.IO) {
            val token = settings.githubToken
            if (token.isBlank()) {
                throw IllegalStateException("GitHub token set nahi hai. Menu (⋮) → Settings me PAT daalo.")
            }

            val appKey = FirebaseClient.appKeyFor(prompt)
            val client = GitHubClient(token)

            onStep("GitHub Actions trigger kar rahe hain…")
            val dispatchedAt = System.currentTimeMillis()
            client.dispatchBuild(prompt)

            onStep("Build queue me daal diya. Run ka intezaar…")
            val run = awaitRun(client, dispatchedAt, onStep)
            if (!run.isSuccess) {
                throw IOException(
                    "Build ${run.conclusion ?: "fail"} hua. Logs: ${run.htmlUrl}"
                )
            }

            onStep("Build successful ✅ Download link fetch kar rahe hain…")
            val dbUrl = settings.firebaseDbUrl
            if (dbUrl.isBlank()) {
                throw IllegalStateException(
                    "Firebase DB URL blank hai. gradle.properties me FIREBASE_DB_URL set karo."
                )
            }

            val record = awaitRecord(dbUrl, appKey, onStep)
            val driveUrl = resolveDownloadUrl(record, appKey, run)

            onStep("Link ready. Publish kar rahe hain…")
            val finalUrl = publish(driveUrl, prompt, onStep)

            BuildResult(
                appName = prompt,
                appKey = appKey,
                downloadUrl = finalUrl,
                runUrl = run.htmlUrl
            )
        }

    // ------------------------------------------------------------------ runs

    private suspend fun awaitRun(
        client: GitHubClient,
        dispatchedAt: Long,
        onStep: (String) -> Unit,
    ): WorkflowRun {
        val deadline = System.currentTimeMillis() + AppConfig.BUILD_TIMEOUT_MS
        var runId: Long? = null
        var lastStatus: String? = null

        while (System.currentTimeMillis() < deadline) {
            delay(AppConfig.POLL_INTERVAL_MS)

            val run = if (runId == null) {
                runCatching { client.findLatestRun(dispatchedAt) }.getOrNull()?.also { runId = it.id }
            } else {
                runCatching { client.getRun(runId!!) }.getOrNull()
            } ?: continue

            if (run.status != lastStatus) {
                lastStatus = run.status
                onStep(
                    when {
                        run.isFinished -> "Build complete: ${run.conclusion ?: "unknown"}"
                        run.status.equals("in_progress", true) -> "App build ho raha hai… (chand minute)"
                        else -> "Build status: ${run.status}"
                    }
                )
            }
            if (run.isFinished) return run
        }

        throw IOException(
            "Timeout: build ${AppConfig.BUILD_TIMEOUT_MS / 60_000} minute me complete nahi hua."
        )
    }

    // -------------------------------------------------------------- firebase

    private suspend fun awaitRecord(dbUrl: String, appKey: String, onStep: (String) -> Unit): AppRecord? {
        var record: AppRecord? = null
        repeat(AppConfig.FIREBASE_RETRIES) { attempt ->
            record = FirebaseClient.fetchAppRecord(dbUrl, appKey)
            if (record?.driveFileId != null || record?.downloadUrl != null) return record
            if (attempt < AppConfig.FIREBASE_RETRIES - 1) {
                onStep("Firebase sync ka intezaar… (${attempt + 1}/${AppConfig.FIREBASE_RETRIES})")
                delay(AppConfig.FIREBASE_RETRY_DELAY_MS)
            }
        }
        return record
    }

    private fun resolveDownloadUrl(
        record: AppRecord?,
        appKey: String,
        run: WorkflowRun,
    ): String {
        record?.downloadUrl?.takeIf { it.isNotBlank() }?.let { return it }
        record?.driveFileId?.takeIf { it.isNotBlank() }?.let { fileId ->
            return "https://drive.google.com/uc?export=download&id=$fileId"
        }
        throw IOException(
            "Firebase me `apps/$appKey` par APK link nahi mila. " +
                "Workflow ka \"Save Link to Firebase\" step check karo. Run: ${run.htmlUrl}"
        )
    }

    // ---------------------------------------------------------------- upload

    /** Agar SAIF_UPLOAD_URL set hai to APK download karke wahan upload karta hai. */
    private suspend fun publish(driveUrl: String, appName: String, onStep: (String) -> Unit): String {
        if (AppConfig.saifUploadUrl.isBlank()) return driveUrl

        val apk = runCatching {
            onStep("APK download kar rahe hain (Saif par upload ke liye)…")
            val dir = File(cacheDir, "builds").apply { mkdirs() }
            val safeName = appName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            Http.downloadFile(driveUrl, File(dir, "$safeName.apk"))
        }.getOrNull() ?: return driveUrl

        onStep("Saif server par upload ho raha hai…")
        val uploadedUrl = SaifUploader.uploadToSaif(apk, appName)
        apk.delete()
        return uploadedUrl ?: driveUrl
    }
}
