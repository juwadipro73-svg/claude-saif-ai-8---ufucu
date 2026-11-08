package com.saif.ai.builder.net

import com.saif.ai.builder.AppConfig
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class WorkflowRun(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String,
    val createdAtMillis: Long,
) {
    val isFinished: Boolean get() = status.equals("completed", ignoreCase = true)
    val isSuccess: Boolean get() = conclusion.equals("success", ignoreCase = true)
}

class GitHubClient(private val token: String) {

    private val base = "https://api.github.com"
    private val repoSlug = "${AppConfig.githubOwner}/${AppConfig.githubRepo}"

    private fun headers(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28"
    )

    /** workflow_dispatch trigger — 204 No Content return karta hai. */
    fun dispatchBuild(prompt: String) {
        val url = "$base/repos/$repoSlug/actions/workflows/${AppConfig.githubWorkflow}/dispatches"
        val payload = JSONObject()
            .put("ref", AppConfig.githubBranch)
            .put("inputs", JSONObject().put("prompt", prompt))
            .toString()

        val response = Http.request(url, method = "POST", headers = headers(), body = payload)
        if (response.code != 204 && !response.isSuccessful) {
            val hint = when (response.code) {
                401 -> "Token galat/expired hai."
                403 -> "Token me Actions: write permission nahi hai."
                404 -> "Repo/workflow nahi mila (${AppConfig.repoSlug}/${AppConfig.githubWorkflow})."
                422 -> "Workflow inputs match nahi kar rahe."
                else -> response.body.take(300)
            }
            throw IOException("GitHub dispatch failed (HTTP ${response.code}): $hint")
        }
    }

    /** Dispatch ke baad naya run dhoondta hai. */
    fun findLatestRun(dispatchedAfterMillis: Long): WorkflowRun? {
        val url = "$base/repos/$repoSlug/actions/runs" +
            "?event=workflow_dispatch&branch=${AppConfig.githubBranch}&per_page=20"
        val response = Http.request(url, headers = headers())
        if (!response.isSuccessful) {
            throw IOException("Run list failed (HTTP ${response.code}): ${response.body.take(200)}")
        }
        val runs = JSONObject(response.body).optJSONArray("workflow_runs") ?: return null
        var best: WorkflowRun? = null
        for (index in 0 until runs.length()) {
            val run = parseRun(runs.optJSONObject(index) ?: continue)
            // Clock skew ke liye 2 min ka margin.
            if (run.createdAtMillis + 120_000 < dispatchedAfterMillis) continue
            if (best == null || run.id > best.id) best = run
        }
        return best
    }

    fun getRun(runId: Long): WorkflowRun {
        val response = Http.request("$base/repos/$repoSlug/actions/runs/$runId", headers = headers())
        if (!response.isSuccessful) {
            throw IOException("Run status failed (HTTP ${response.code})")
        }
        return parseRun(JSONObject(response.body))
    }

    private fun parseRun(json: JSONObject): WorkflowRun = WorkflowRun(
        id = json.optLong("id"),
        status = json.optString("status", "unknown"),
        conclusion = json.optString("conclusion").takeIf { it.isNotBlank() && it != "null" },
        htmlUrl = json.optString("html_url"),
        createdAtMillis = parseIso(json.optString("created_at"))
    )

    private fun parseIso(value: String): Long {
        if (value.isBlank()) return 0L
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value)?.time ?: 0L
        }.getOrDefault(0L)
    }
}
