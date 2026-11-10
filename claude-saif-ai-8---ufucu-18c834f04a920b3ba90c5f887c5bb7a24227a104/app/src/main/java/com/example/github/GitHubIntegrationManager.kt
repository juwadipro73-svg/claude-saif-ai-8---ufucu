/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.github

import android.content.Context
import android.content.SharedPreferences
import com.example.gemini.data.daemon.GitApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class GitHubAccountProfile(
    val login: String,
    val name: String,
    val email: String,
    val avatarUrl: String,
    val htmlUrl: String,
    val publicRepos: Int,
    val privateRepos: Int,
    val connectedAt: Long
)

data class GitHubRepoSummary(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val cloneUrl: String,
    val htmlUrl: String,
    val language: String,
    val updatedAt: String
)

data class GitHubDeviceFlowChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSec: Int,
    val intervalSec: Int
)

data class GitHubConnectionState(
    val isConnected: Boolean = false,
    val isBusy: Boolean = false,
    val profile: GitHubAccountProfile? = null,
    val repositories: List<GitHubRepoSummary> = emptyList(),
    val clonedRepoPaths: Map<String, String> = emptyMap(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

object GitHubIntegrationManager {
    private const val PREFS_NAME = "saif_github_integration"
    private const val KEY_TOKEN = "gh_token"
    private const val KEY_LOGIN = "gh_login"
    private const val KEY_NAME = "gh_name"
    private const val KEY_EMAIL = "gh_email"
    private const val KEY_AVATAR = "gh_avatar"
    private const val KEY_HTML_URL = "gh_html_url"
    private const val KEY_PUB_REPOS = "gh_pub_repos"
    private const val KEY_PRIV_REPOS = "gh_priv_repos"
    private const val KEY_CONNECTED_AT = "gh_connected_at"
    private const val KEY_OAUTH_CLIENT_ID = "gh_oauth_client_id"

    const val NEW_TOKEN_BROWSER_URL =
        "https://github.com/settings/tokens/new?description=SAIF+AI+Workspace&scopes=repo,workflow,read:user,user:email"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var cachedToken: String? = null

    private val _state = MutableStateFlow(GitHubConnectionState())
    val state: StateFlow<GitHubConnectionState> = _state.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    val sp = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs = sp
                    val token = sp.getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotBlank() }
                    val login = sp.getString(KEY_LOGIN, null)?.trim()?.takeIf { it.isNotBlank() }
                    cachedToken = token
                    if (token != null && login != null) {
                        val profile = GitHubAccountProfile(
                            login = login,
                            name = sp.getString(KEY_NAME, login) ?: login,
                            email = sp.getString(KEY_EMAIL, "$login@users.noreply.github.com")
                                ?: "$login@users.noreply.github.com",
                            avatarUrl = sp.getString(KEY_AVATAR, "") ?: "",
                            htmlUrl = sp.getString(KEY_HTML_URL, "https://github.com/$login")
                                ?: "https://github.com/$login",
                            publicRepos = sp.getInt(KEY_PUB_REPOS, 0),
                            privateRepos = sp.getInt(KEY_PRIV_REPOS, 0),
                            connectedAt = sp.getLong(KEY_CONNECTED_AT, 0L)
                        )
                        _state.value = GitHubConnectionState(
                            isConnected = true,
                            profile = profile,
                            clonedRepoPaths = discoverLocalWorkspaceClones(appContext)
                        )
                        scope.launch {
                            syncCredentialsToLinuxWorkspace(appContext, token, profile)
                        }
                    }
                }
            }
        }
    }

    fun getTokenOrNull(): String? = cachedToken?.takeIf { it.isNotBlank() }

    fun getSavedOAuthClientId(context: Context): String {
        init(context)
        return prefs?.getString(KEY_OAUTH_CLIENT_ID, "")?.trim().orEmpty()
    }

    fun saveOAuthClientId(context: Context, clientId: String) {
        init(context)
        prefs?.edit()?.putString(KEY_OAUTH_CLIENT_ID, clientId.trim())?.apply()
    }

    fun sanitizeTokenInput(raw: String): String {
        return raw.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .removePrefix("token ")
            .trim('"', '\'', ' ', '\n', '\r', '\t')
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, statusMessage = null)
    }

    suspend fun connectWithToken(context: Context, rawToken: String): Result<GitHubAccountProfile> =
        withContext(Dispatchers.IO) {
            init(context)
            val token = sanitizeTokenInput(rawToken)
            if (token.length < 12 || token.any { it.isWhitespace() }) {
                val msg = "Paste a valid GitHub Personal Access Token (starts with ghp_ or github_pat_)."
                _state.value = _state.value.copy(isBusy = false, errorMessage = msg)
                return@withContext Result.failure(IllegalArgumentException(msg))
            }

            _state.value = _state.value.copy(isBusy = true, errorMessage = null, statusMessage = "Verifying GitHub account…")
            try {
                val profile = fetchAuthenticatedUser(token)
                persistConnection(token, profile)
                syncCredentialsToLinuxWorkspace(context.applicationContext, token, profile)
                val repos = runCatching { fetchRepositoriesInternal(token) }.getOrDefault(emptyList())
                _state.value = GitHubConnectionState(
                    isConnected = true,
                    isBusy = false,
                    profile = profile,
                    repositories = repos,
                    clonedRepoPaths = discoverLocalWorkspaceClones(context.applicationContext),
                    statusMessage = "Connected as @${profile.login} & synced with Linux Git!"
                )
                Result.success(profile)
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Failed to connect to GitHub. Check your token and internet."
                _state.value = _state.value.copy(isBusy = false, errorMessage = msg, statusMessage = null)
                Result.failure(e)
            }
        }

    suspend fun refreshRepositories(context: Context): Result<List<GitHubRepoSummary>> =
        withContext(Dispatchers.IO) {
            init(context)
            val token = cachedToken ?: return@withContext Result.failure(IllegalStateException("GitHub is not connected"))
            _state.value = _state.value.copy(isBusy = true, errorMessage = null)
            try {
                val repos = fetchRepositoriesInternal(token)
                _state.value = _state.value.copy(
                    isBusy = false,
                    repositories = repos,
                    clonedRepoPaths = discoverLocalWorkspaceClones(context.applicationContext)
                )
                Result.success(repos)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isBusy = false, errorMessage = e.localizedMessage ?: "Could not load repositories")
                Result.failure(e)
            }
        }

    suspend fun createRepository(
        context: Context,
        repoName: String,
        description: String,
        isPrivate: Boolean
    ): Result<GitHubRepoSummary> = withContext(Dispatchers.IO) {
        init(context)
        val token = cachedToken ?: return@withContext Result.failure(IllegalStateException("Connect GitHub first"))
        val cleanName = repoName.trim().replace(Regex("[^A-Za-z0-9._-]"), "-")
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid repository name"))
        }
        _state.value = _state.value.copy(isBusy = true, errorMessage = null, statusMessage = "Creating $cleanName on GitHub…")
        try {
            val payload = JSONObject()
                .put("name", cleanName)
                .put("description", description.trim())
                .put("private", isPrivate)
                .put("auto_init", true)
            val req = Request.Builder()
                .url("https://api.github.com/user/repos")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SAIF-AI-Android")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val created = httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val apiErr = runCatching { JSONObject(bodyStr).optString("message") }.getOrNull()
                    error(apiErr?.takeIf { it.isNotBlank() } ?: "GitHub returned HTTP ${resp.code}")
                }
                parseRepoSummary(JSONObject(bodyStr))
            }
            val updatedList = listOf(created) + _state.value.repositories.filterNot { it.fullName == created.fullName }
            _state.value = _state.value.copy(
                isBusy = false,
                repositories = updatedList,
                statusMessage = "Created ${created.fullName}!"
            )
            Result.success(created)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isBusy = false, errorMessage = e.localizedMessage ?: "Repository creation failed")
            Result.failure(e)
        }
    }

    /**
     * Clones a GitHub repository directly into `/root/workspace/<repo>` (`filesDir/home/<repo>`),
     * working reliably whether or not Linux `git` has finished installing.
     */
    suspend fun cloneRepositoryToWorkspace(
        context: Context,
        repo: GitHubRepoSummary
    ): Result<String> = withContext(Dispatchers.IO) {
        init(context)
        val token = cachedToken ?: return@withContext Result.failure(IllegalStateException("Connect GitHub first"))
        val appContext = context.applicationContext
        _state.value = _state.value.copy(
            isBusy = true,
            errorMessage = null,
            statusMessage = "Cloning ${repo.fullName} into /root/workspace/${repo.name}…"
        )
        try {
            val homeDir = File(appContext.filesDir, "home").apply { mkdirs() }
            val safeFolder = repo.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "repo" }
            val targetDir = File(homeDir, safeFolder)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            val branch = repo.defaultBranch.ifBlank { "main" }
            val zipUrl = "https://api.github.com/repos/${repo.fullName}/zipball/${URLEncoder.encode(branch, "UTF-8")}"
            val req = Request.Builder()
                .url(zipUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SAIF-AI-Android")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Download failed (HTTP ${resp.code}). Verify repository permissions.")
                }
                val stream = resp.body?.byteStream() ?: error("Empty archive from GitHub")
                ZipInputStream(stream.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    var extractedFiles = 0
                    val targetCanonical = targetDir.canonicalPath
                    while (entry != null) {
                        val rawName = entry.name.replace('\\', '/').trimStart('/')
                        // GitHub zipballs wrap files inside a top-level `<owner>-<repo>-<sha>/` folder
                        val relPath = if (rawName.contains('/')) rawName.substringAfter('/') else ""
                        if (relPath.isNotBlank()) {
                            val outFile = File(targetDir, relPath)
                            val outCanonical = outFile.canonicalPath
                            if (!outCanonical.startsWith("$targetCanonical${File.separator}") && outCanonical != targetCanonical) {
                                throw SecurityException("Blocked unsafe path in archive: $relPath")
                            }
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out -> zis.copyTo(out) }
                                extractedFiles++
                                if (extractedFiles > 10_000) break
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // Write lightweight git remote metadata note & also mirror symlink/reference hint so Linux git knows origin
            val profile = _state.value.profile
            if (profile != null) {
                syncCredentialsToLinuxWorkspace(appContext, token, profile)
            }
            File(targetDir, ".saif-github-origin").writeText(
                "repo=${repo.fullName}\nremote=${repo.cloneUrl}\nbranch=$branch\n"
            )

            val linuxPath = "/root/workspace/$safeFolder"
            val updatedClones = discoverLocalWorkspaceClones(appContext) + (repo.fullName to linuxPath)
            _state.value = _state.value.copy(
                isBusy = false,
                clonedRepoPaths = updatedClones,
                statusMessage = "Cloned ${repo.fullName} to $linuxPath"
            )
            Result.success(linuxPath)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isBusy = false,
                errorMessage = e.localizedMessage ?: "Failed to clone ${repo.fullName}"
            )
            Result.failure(e)
        }
    }

    suspend fun startDeviceFlow(clientId: String): Result<GitHubDeviceFlowChallenge> =
        withContext(Dispatchers.IO) {
            val cleanId = clientId.trim()
            if (cleanId.length < 8) {
                return@withContext Result.failure(IllegalArgumentException("Enter a valid GitHub OAuth Client ID"))
            }
            try {
                val form = FormBody.Builder()
                    .add("client_id", cleanId)
                    .add("scope", "repo workflow read:user user:email")
                    .build()
                val req = Request.Builder()
                    .url("https://github.com/login/device/code")
                    .header("Accept", "application/json")
                    .header("User-Agent", "SAIF-AI-Android")
                    .post(form)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    if (!resp.isSuccessful || json.has("error")) {
                        error(json.optString("error_description").ifBlank { "Device flow unavailable (HTTP ${resp.code})" })
                    }
                    Result.success(
                        GitHubDeviceFlowChallenge(
                            deviceCode = json.getString("device_code"),
                            userCode = json.getString("user_code"),
                            verificationUri = json.optString("verification_uri", "https://github.com/login/device"),
                            expiresInSec = json.optInt("expires_in", 900),
                            intervalSec = json.optInt("interval", 5).coerceAtLeast(5)
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun pollDeviceFlowOnce(
        context: Context,
        clientId: String,
        deviceCode: String
    ): Result<GitHubAccountProfile?> = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("client_id", clientId.trim())
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val req = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .header("User-Agent", "SAIF-AI-Android")
                .post(form)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string().orEmpty())
                val accessToken = json.optString("access_token")
                if (accessToken.isNotBlank()) {
                    val connected = connectWithToken(context, accessToken).getOrThrow()
                    return@withContext Result.success(connected)
                }
                val err = json.optString("error")
                when (err) {
                    "authorization_pending", "slow_down" -> Result.success(null)
                    else -> Result.failure(IllegalStateException(json.optString("error_description").ifBlank { err.ifBlank { "Authorization failed" } }))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnect(context: Context) {
        init(context)
        cachedToken = null
        prefs?.edit()
            ?.remove(KEY_TOKEN)
            ?.remove(KEY_LOGIN)
            ?.remove(KEY_NAME)
            ?.remove(KEY_EMAIL)
            ?.remove(KEY_AVATAR)
            ?.remove(KEY_HTML_URL)
            ?.remove(KEY_PUB_REPOS)
            ?.remove(KEY_PRIV_REPOS)
            ?.remove(KEY_CONNECTED_AT)
            ?.apply()
        _state.value = GitHubConnectionState(isConnected = false, statusMessage = "Disconnected from GitHub")
        scope.launch {
            removeCredentialsFromLinuxWorkspace(context.applicationContext)
        }
    }

    private fun fetchAuthenticatedUser(token: String): GitHubAccountProfile {
        val userReq = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SAIF-AI-Android")
            .get()
            .build()

        val userJson = httpClient.newCall(userReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (resp.code == 401 || resp.code == 403) {
                error("Invalid or expired GitHub token. Generate a token with 'repo' and 'read:user' scopes.")
            }
            if (!resp.isSuccessful) {
                error("GitHub API error (HTTP ${resp.code}). Please try again.")
            }
            JSONObject(bodyStr)
        }

        val login = userJson.optString("login").trim()
        require(login.isNotBlank()) { "GitHub did not return an account username" }
        val userId = userJson.optLong("id", 0L)
        val displayName = userJson.optString("name")
            .takeIf { it.isNotBlank() && it != "null" } ?: login
        var email = userJson.optString("email")
            .takeIf { it.isNotBlank() && it != "null" }.orEmpty()

        if (email.isBlank()) {
            email = runCatching { fetchPrimaryEmail(token) }.getOrNull().orEmpty()
        }
        if (email.isBlank()) {
            email = if (userId > 0L) "$userId+$login@users.noreply.github.com" else "$login@users.noreply.github.com"
        }

        val publicRepos = userJson.optInt("public_repos", 0)
        val privateRepos = userJson.optInt("total_private_repos", userJson.optInt("owned_private_repos", 0))

        return GitHubAccountProfile(
            login = login,
            name = displayName,
            email = email,
            avatarUrl = userJson.optString("avatar_url", ""),
            htmlUrl = userJson.optString("html_url", "https://github.com/$login"),
            publicRepos = publicRepos,
            privateRepos = privateRepos,
            connectedAt = System.currentTimeMillis()
        )
    }

    private fun fetchPrimaryEmail(token: String): String? {
        val req = Request.Builder()
            .url("https://api.github.com/user/emails")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SAIF-AI-Android")
            .get()
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val arr = JSONArray(resp.body?.string().orEmpty())
            var fallback: String? = null
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val addr = item.optString("email").takeIf { it.isNotBlank() } ?: continue
                if (item.optBoolean("primary") && item.optBoolean("verified")) return@use addr
                if (fallback == null) fallback = addr
            }
            fallback
        }
    }

    private fun fetchRepositoriesInternal(token: String): List<GitHubRepoSummary> {
        val req = Request.Builder()
            .url("https://api.github.com/user/repos?sort=updated&per_page=35&affiliation=owner,collaborator,organization_member")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SAIF-AI-Android")
            .get()
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Could not list repositories (HTTP ${resp.code})")
            val arr = JSONArray(resp.body?.string().orEmpty())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(parseRepoSummary(obj))
                }
            }
        }
    }

    private fun parseRepoSummary(obj: JSONObject): GitHubRepoSummary {
        val fullName = obj.optString("full_name")
        return GitHubRepoSummary(
            id = obj.optLong("id", 0L),
            name = obj.optString("name"),
            fullName = fullName,
            description = obj.optString("description").takeIf { it != "null" }.orEmpty(),
            isPrivate = obj.optBoolean("private", false),
            defaultBranch = obj.optString("default_branch", "main").ifBlank { "main" },
            cloneUrl = obj.optString("clone_url", "https://github.com/$fullName.git"),
            htmlUrl = obj.optString("html_url", "https://github.com/$fullName"),
            language = obj.optString("language").takeIf { it != "null" }.orEmpty(),
            updatedAt = obj.optString("updated_at", "").take(10)
        )
    }

    private fun persistConnection(token: String, profile: GitHubAccountProfile) {
        cachedToken = token
        prefs?.edit()
            ?.putString(KEY_TOKEN, token)
            ?.putString(KEY_LOGIN, profile.login)
            ?.putString(KEY_NAME, profile.name)
            ?.putString(KEY_EMAIL, profile.email)
            ?.putString(KEY_AVATAR, profile.avatarUrl)
            ?.putString(KEY_HTML_URL, profile.htmlUrl)
            ?.putInt(KEY_PUB_REPOS, profile.publicRepos)
            ?.putInt(KEY_PRIV_REPOS, profile.privateRepos)
            ?.putLong(KEY_CONNECTED_AT, profile.connectedAt)
            ?.apply()
    }

    /**
     * Writes GitHub credentials and Git identity into SAIF AI's Linux rootfs (`/root`)
     * and workspace (`filesDir/home`) so `git clone/pull/push` and `gh` work seamlessly
     * for Antigravity commands in chat.
     */
    suspend fun syncCredentialsToLinuxWorkspace(
        context: Context,
        token: String,
        profile: GitHubAccountProfile
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val encUser = URLEncoder.encode(profile.login, "UTF-8")
            val encToken = URLEncoder.encode(token, "UTF-8")
            val credentialLine = "https://$encUser:$encToken@github.com\n"
            val safeName = profile.name.replace("\"", "").replace("\n", " ").trim().ifBlank { profile.login }
            val safeEmail = profile.email.replace("\"", "").replace("\n", "").trim()

            val gitConfigContent = buildString {
                appendLine("[user]")
                appendLine("\tname = $safeName")
                appendLine("\temail = $safeEmail")
                appendLine("[credential]")
                appendLine("\thelper = store")
                appendLine("[init]")
                appendLine("\tdefaultBranch = main")
                appendLine("[safe]")
                appendLine("\tdirectory = *")
            }

            val ghHostsYml = buildString {
                appendLine("github.com:")
                appendLine("    oauth_token: $token")
                appendLine("    user: ${profile.login}")
                appendLine("    git_protocol: https")
            }

            val targets = listOf(
                File(context.filesDir, "ubuntu/root"),
                File(context.filesDir, "home")
            )
            for (dir in targets) {
                dir.mkdirs()
                File(dir, ".gitconfig").writeText(gitConfigContent)
                val credFile = File(dir, ".git-credentials")
                credFile.writeText(credentialLine)
                credFile.setReadable(false, false)
                credFile.setReadable(true, true)
                credFile.setWritable(false, false)
                credFile.setWritable(true, true)

                val ghDir = File(dir, ".config/gh").apply { mkdirs() }
                val hostsFile = File(ghDir, "hosts.yml")
                hostsFile.writeText(ghHostsYml)
                hostsFile.setReadable(false, false)
                hostsFile.setReadable(true, true)
            }

            val profileDir = File(context.filesDir, "ubuntu/etc/profile.d").apply { mkdirs() }
            val envFile = File(profileDir, "saif_github.sh")
            val escapedToken = token.replace("'", "'\"'\"'")
            val escapedName = safeName.replace("'", "'\"'\"'")
            val escapedEmail = safeEmail.replace("'", "'\"'\"'")
            envFile.writeText(
                buildString {
                    appendLine("#!/bin/sh")
                    appendLine("export GITHUB_TOKEN='$escapedToken'")
                    appendLine("export GH_TOKEN='$escapedToken'")
                    appendLine("export GIT_AUTHOR_NAME='$escapedName'")
                    appendLine("export GIT_AUTHOR_EMAIL='$escapedEmail'")
                    appendLine("export GIT_COMMITTER_NAME='$escapedName'")
                    appendLine("export GIT_COMMITTER_EMAIL='$escapedEmail'")
                }
            )
            envFile.setReadable(false, false)
            envFile.setReadable(true, true)

            // Also sync to running local Git daemon if online
            runCatching {
                GitApiClient.setConfig(
                    projectPath = "/root",
                    userName = safeName,
                    userEmail = safeEmail,
                    pullRebase = null,
                    remoteUrl = null,
                    isGlobal = true
                )
            }
        }
    }

    private fun removeCredentialsFromLinuxWorkspace(context: Context) {
        runCatching {
            val targets = listOf(
                File(context.filesDir, "ubuntu/root"),
                File(context.filesDir, "home")
            )
            for (dir in targets) {
                File(dir, ".git-credentials").delete()
                File(dir, ".config/gh/hosts.yml").delete()
            }
            File(context.filesDir, "ubuntu/etc/profile.d/saif_github.sh").delete()
        }
    }

    private fun discoverLocalWorkspaceClones(context: Context): Map<String, String> {
        val result = mutableMapOf<String, String>()
        runCatching {
            val homeDir = File(context.filesDir, "home")
            if (!homeDir.isDirectory) return emptyMap()
            homeDir.listFiles()?.forEach { child ->
                if (!child.isDirectory) return@forEach
                val marker = File(child, ".saif-github-origin")
                if (marker.isFile) {
                    val repoLine = marker.readLines().firstOrNull { it.startsWith("repo=") }
                    val fullName = repoLine?.removePrefix("repo=")?.trim()
                    if (!fullName.isNullOrBlank()) {
                        result[fullName] = "/root/workspace/${child.name}"
                    }
                }
            }
        }
        return result
    }
}
