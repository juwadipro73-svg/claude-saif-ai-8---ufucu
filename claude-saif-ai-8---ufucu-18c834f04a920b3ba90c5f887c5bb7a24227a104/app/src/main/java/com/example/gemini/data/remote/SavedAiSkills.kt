/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.data.remote

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.mutableStateListOf
import com.example.data.local.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A user-approved, single-file snapshot of a GitHub skill. Chat never fetches a repository implicitly. */
data class SavedAiSkill(
    val name: String,
    val description: String,
    val sourceUrl: String,
    val instructions: String,
    val savedAt: Long
)

/** Only a leading /command selects a saved skill. The remainder is the user's task, not part of the skill. */
object SkillSlashParsing {
    private val LEADING = Regex("^\\s*/([A-Za-z0-9][A-Za-z0-9_-]{0,47})(?=\\s|$)")

    data class Invocation(val command: String, val task: String)

    fun parse(text: String): Invocation? {
        val match = LEADING.find(text) ?: return null
        return Invocation(match.groupValues[1], text.substring(match.range.last + 1).trim())
    }

    fun query(text: String): String? {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith('/') || trimmed.drop(1).any { it.isWhitespace() }) return null
        return trimmed.drop(1)
    }
}

/** Locally stored, app-account-scoped skill bundles. Each command has exactly one .md file in app-private storage. */
object SavedAiSkills {
    private const val PREFS = "saif_saved_ai_skills"
    private const val MAX_SKILLS = 100
    private const val MAX_API_BYTES = 4 * 1024 * 1024
    private const val MAX_SINGLE_FILE_BYTES = 1024 * 1024
    private const val MAX_SKILL_ROOTS = 40
    private val safeSegment = Regex("[A-Za-z0-9_.-]{1,120}")
    private val binaryExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "heic", "ico", "pdf",
        "zip", "gz", "tar", "jar", "class", "apk", "mp4", "mov", "mp3", "wav", "ogg", "flac", "ttf",
        "woff", "woff2", "otf", "so", "bin", "exe", "sqlite", "db", "psd")
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    val skills = mutableStateListOf<SavedAiSkill>()
    private var appContext: Context? = null
    private var key: String? = null
    @Volatile private var boundUid: String? = null

    private fun isCurrentAccount(): Boolean = boundUid != null &&
        AuthManager.currentUser.value?.let { it.uid == boundUid && it.isActive && !it.isBanned } == true

    private fun fileFor(storageKey: String, name: String): File =
        File(requireNotNull(appContext).filesDir, "saved-ai-skills/$storageKey/${name.lowercase(Locale.ROOT)}.md")

    private fun writeFile(storageKey: String, name: String, instructions: String) {
        val bytes = instructions.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= SkillBundle.IMPORT_MAX_BYTES) { "Skill exceeds 4 MB; nothing was saved" }
        val file = fileFor(storageKey, name)
        check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true) { "Could not create skill folder" }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    fun bind(context: Context, uid: String?) {
        val account = uid?.takeIf { it.isNotBlank() && AuthManager.currentUser.value?.let { user ->
            user.uid == it && user.isActive && !user.isBanned
        } == true }
        val newKey = account?.let { "skills_" + MessageDigest.getInstance("SHA-256")
            .digest(it.toByteArray(Charsets.UTF_8)).joinToString("") { b -> "%02x".format(b) } }
        if (appContext != null && key == newKey) return
        appContext = context.applicationContext
        boundUid = null
        key = newKey
        skills.clear()
        boundUid = account
        if (newKey == null) return
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(newKey, null) ?: return
        runCatching {
            val array = JSONArray(stored)
            var migrated = false
            val loaded = (0 until minOf(array.length(), MAX_SKILLS)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name")
                if (!CustomSlashCommands.NAME.matches(name)) return@mapNotNull null
                val file = fileFor(newKey, name)
                val fromFile = if (file.isFile && file.length() in 1..SkillBundle.IMPORT_MAX_BYTES.toLong())
                    runCatching { SkillBundle.decodeText(file.readBytes()) }.getOrNull() else null
                // Previous releases stored each SKILL.md directly in preferences. Migrate it once;
                // never drop the legacy text if disk is full during the first migration.
                val legacy = item.optString("instructions").takeIf {
                    it.isNotBlank() && it.toByteArray(Charsets.UTF_8).size <= SkillBundle.IMPORT_MAX_BYTES
                }
                val text = fromFile ?: legacy ?: return@mapNotNull null
                if (fromFile == null && legacy != null && runCatching { writeFile(newKey, name, legacy) }.isSuccess)
                    migrated = true
                SavedAiSkill(name, item.optString("description"), item.optString("sourceUrl"),
                    text, item.optLong("savedAt"))
            }.sortedByDescending { it.savedAt }
            skills.addAll(loaded)
            if (migrated) persist()
        }
    }

    fun visibleSkills(): List<SavedAiSkill> = if (isCurrentAccount()) skills.toList() else emptyList()

    fun find(name: String): SavedAiSkill? = visibleSkills().firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun suggestions(query: String): List<SlashCommandItem> = visibleSkills().filter {
        query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true)
    }.sortedByDescending { it.savedAt }.map {
        SlashCommandItem(it.name, "/${it.name}", it.description, "saved_skill", argumentHint = "your request")
    }

    /** Compatibility with explicit file links; browser folder/repo links now use [importFromGitHub]. */
    fun githubRawUrl(input: String): String = GithubSkillLinks.rawFileUrl(input)

    /** Explicit description variant; imports the same single bundle as the Settings screen. */
    suspend fun fetchAndSave(nameInput: String, descriptionInput: String, linkInput: String): SavedAiSkill {
        require(descriptionInput.trim().isNotBlank() && descriptionInput.length <= 500) { "Enter a short description" }
        val imported = importFromGitHub(linkInput, nameInput)
        val updated = imported.copy(description = descriptionInput.trim())
        save(updated)
        return updated
    }

    private fun checkedRawUrl(url: String, owner: String, repo: String, path: String): String {
        val raw = url.toHttpUrlOrNull() ?: error("Invalid GitHub file URL")
        val pathParts = path.split('/')
        require(raw.isHttps && raw.host == "raw.githubusercontent.com" && raw.username.isEmpty() &&
            raw.password.isEmpty() && raw.query == null && raw.fragment == null &&
            raw.pathSegments.size >= pathParts.size + 3 &&
            raw.pathSegments[0].equals(owner, true) && raw.pathSegments[1].equals(repo, true) &&
            raw.pathSegments.takeLast(pathParts.size) == pathParts &&
            raw.pathSegments.all { it.isNotBlank() && it != "." && it != ".." && '\\' !in it }) {
            "GitHub returned an invalid file address"
        }
        return raw.toString()
    }

    private fun checkedRedirect(url: String): String {
        val raw = url.toHttpUrlOrNull() ?: error("GitHub returned an invalid redirect")
        require(raw.isHttps && raw.host == "raw.githubusercontent.com" && raw.username.isEmpty() &&
            raw.password.isEmpty() && raw.query == null && raw.fragment == null &&
            raw.pathSegments.size >= 4 && raw.pathSegments.all { it.isNotBlank() && it != "." && it != ".." && '\\' !in it }) {
            "GitHub redirected outside its raw file host"
        }
        return raw.toString()
    }

    /** Cap both declared and streamed sizes; don't redirect to unrelated hosts or private networks. */
    private suspend fun fetchGitHubBytes(source: String): ByteArray = withContext(Dispatchers.IO) {
        var url = checkedRedirect(source)
        val ghToken = com.example.github.GitHubIntegrationManager.getTokenOrNull()
        repeat(3) {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "text/plain")
                .header("User-Agent", "SAIF-AI-Skill-Importer")
            if (!ghToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $ghToken")
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.code in 300..399) {
                    val next = response.header("Location") ?: error("GitHub returned an invalid redirect")
                    url = checkedRedirect(URI(url).resolve(next).toString())
                } else {
                    if (!response.isSuccessful) error("GitHub returned HTTP ${response.code}. Check that the files are accessible.")
                    val body = response.body ?: error("GitHub returned an empty file")
                    val out = ByteArrayOutputStream()
                    body.byteStream().use { stream ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            val remaining = MAX_SINGLE_FILE_BYTES - out.size()
                            if (remaining <= 0) break
                            out.write(buffer, 0, minOf(count, remaining))
                        }
                    }
                    return@withContext out.toByteArray()
                }
            }
        }
        error("Too many GitHub redirects")
    }

    /** Only GitHub's API is queried for folder listings; redirects and oversized listings are rejected. */
    private suspend fun apiJson(url: HttpUrl): String? = withContext(Dispatchers.IO) {
        check(url.host == "api.github.com" && url.isHttps) { "Invalid GitHub API endpoint" }
        val ghToken = com.example.github.GitHubIntegrationManager.getTokenOrNull()
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SAIF-AI-Skill-Importer")
        if (!ghToken.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $ghToken")
        }
        client.newCall(reqBuilder.build()).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (response.code == 403 || response.code == 429) {
                error("GitHub API rate limit reached. Connect your GitHub icon in the top header for 5,000 requests/hour.")
            }
            if (!response.isSuccessful) error("GitHub returned HTTP ${response.code}. Check repository URL or connect GitHub for private repos.")
            val body = response.body ?: error("Empty GitHub response")
            if (body.contentLength() > MAX_API_BYTES) error("GitHub folder listing is too large; paste a specific skill folder URL")
            val out = ByteArrayOutputStream()
            body.byteStream().use { stream ->
                val bytes = ByteArray(8192)
                while (true) {
                    val count = stream.read(bytes)
                    if (count < 0) break
                    if (out.size() + count > MAX_API_BYTES) error("GitHub folder listing is too large; paste a specific skill folder URL")
                    out.write(bytes, 0, count)
                }
            }
            out.toString("UTF-8")
        }
    }

    private fun apiUrl(owner: String, repo: String, path: String = "", ref: String? = null): HttpUrl =
        "https://api.github.com/repos/$owner/$repo/contents".toHttpUrl().newBuilder().apply {
            if (path.isNotBlank()) path.split('/').forEach { addPathSegment(it) }
            if (ref != null) addQueryParameter("ref", ref)
        }.build()

    private suspend fun entries(owner: String, repo: String, path: String, ref: String): List<JSONObject>? {
        val json = apiJson(apiUrl(owner, repo, path, ref)) ?: return null
        val array = runCatching { JSONArray(json) }.getOrNull()
            ?: throw IllegalArgumentException("This URL points to a file. Paste its /blob/ link instead.")
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun validPath(path: String): Boolean = path.isNotBlank() &&
        path.split('/').all { part -> part.isNotBlank() && part != "." && part != ".." && part.length <= 160 &&
            part.none { it == '\\' || Character.isISOControl(it) } }

    private data class DiscoveredFile(val path: String, val rawUrl: String, val size: Long)

    /** Recursively gather the files of ONE skill folder, bounded in depth and count. Never create child skills. */
    private suspend fun collectTree(owner: String, repo: String, path: String, ref: String,
        output: MutableList<DiscoveredFile>, depth: Int = 0, listing: List<JSONObject>? = null) {
        if (depth > 7 || output.size >= SkillBundle.IMPORT_MAX_FILES) return
        val items = listing ?: entries(owner, repo, path, ref) ?: return
        for (item in items.sortedBy { it.optString("name") }) {
            if (output.size >= SkillBundle.IMPORT_MAX_FILES) break
            val itemPath = item.optString("path")
            require(validPath(itemPath) && (path.isBlank() || itemPath.startsWith("$path/"))) {
                "GitHub returned an unsafe skill path"
            }
            when (item.optString("type")) {
                "dir" -> collectTree(owner, repo, itemPath, ref, output, depth + 1)
                "file" -> {
                    val raw = item.optString("download_url")
                    if (raw.isNotBlank() && raw != "null")
                        output.add(DiscoveredFile(itemPath, checkedRawUrl(raw, owner, repo, itemPath), item.optLong("size", -1)))
                }
            }
        }
    }

    private suspend fun repoInfo(owner: String, repo: String): Pair<String, String> {
        val metadata = apiJson("https://api.github.com/repos/$owner/$repo".toHttpUrl())
            ?: error("GitHub repository not found or is private")
        val info = JSONObject(metadata)
        val ref = info.optString("default_branch")
        require(ref.isNotBlank() && ref.length <= 120 &&
            ref.split('/').all { safeSegment.matches(it) && it != "." && it != ".." }) {
            "Invalid GitHub default branch"
        }
        val description = info.optString("description").takeIf { it.isNotBlank() && it != "null" }
            ?: "Imported from $owner/$repo"
        return ref to description
    }

    private suspend fun findDirectory(owner: String, repo: String, pieces: List<String>): Pair<String, String> {
        // Branch names can contain '/'. Probe the Contents API; no URL supplied by the repository is trusted.
        for (split in 1 until pieces.size) {
            val ref = pieces.take(split).joinToString("/")
            val path = pieces.drop(split).joinToString("/")
            if (entries(owner, repo, path, ref) != null) return ref to path
        }
        error("Skill folder not found. Check its branch and URL.")
    }

    private suspend fun findFile(owner: String, repo: String, pieces: List<String>): Pair<String, String> {
        for (split in 1 until pieces.size) {
            val ref = pieces.take(split).joinToString("/")
            val path = pieces.drop(split).joinToString("/")
            val json = apiJson(apiUrl(owner, repo, path, ref)) ?: continue
            val item = runCatching { JSONObject(json) }.getOrNull() ?: continue
            if (item.optString("type") == "file" && item.optString("path") == path) return ref to path
        }
        error("Skill file not found. Check its branch and URL.")
    }

    private suspend fun collectRootSkill(owner: String, repo: String, ref: String,
        root: List<JSONObject>, output: MutableList<DiscoveredFile>) {
        // A root SKILL.md is one skill. Don't mistake every source directory in a general-purpose
        // repository for part of the skill; collect root docs plus its typical supporting directories.
        val rootDocs = root.filter { item ->
            val name = item.optString("name")
            item.optString("type") == "file" && (name.equals("LICENSE", true) ||
                listOf(".md", ".txt", ".yaml", ".yml", ".json").any { name.endsWith(it, true) })
        }
        if (rootDocs.isNotEmpty()) collectTree(owner, repo, "", ref, output, listing = rootDocs)
        for (dir in root.filter { it.optString("type") == "dir" &&
            it.optString("name") in setOf("references", "scripts", "templates", "examples", "assets", "agents") }) {
            collectTree(owner, repo, dir.optString("path"), ref, output)
        }
    }

    private suspend fun repositoryFiles(owner: String, repo: String, ref: String): List<DiscoveredFile> {
        val root = entries(owner, repo, "", ref) ?: error("GitHub repository is empty")
        val output = mutableListOf<DiscoveredFile>()
        val hasRootSkill = root.any { it.optString("type") == "file" && it.optString("name").equals("SKILL.md", true) }
        if (hasRootSkill) collectRootSkill(owner, repo, ref, root, output)
        else {
            val rootInstructionFiles = root.filter {
                it.optString("type") == "file" && it.optString("name")
                    .lowercase(Locale.ROOT) in setOf("agents.md", "claude.md", "gemini.md", ".cursorrules", "skill.md")
            }
            if (rootInstructionFiles.isNotEmpty()) collectTree(owner, repo, "", ref, output, listing = rootInstructionFiles)
        }
        var skillRoots = if (hasRootSkill) 1 else 0
        for (candidate in listOf(".agents/skills", ".claude/skills", ".codex/skills", ".cursor/rules", "skills", "prompts")) {
            val listing = entries(owner, repo, candidate, ref) ?: continue
            if (listing.any { it.optString("type") == "file" && it.optString("name").equals("SKILL.md", true) }) {
                collectTree(owner, repo, candidate, ref, output, listing = listing)
                skillRoots++
            } else {
                // Files directly under the skills container are shared context for its children.
                val sharedDocs = listing.filter { it.optString("type") == "file" &&
                    !it.optString("name").equals("README.md", true) }
                if (sharedDocs.isNotEmpty()) collectTree(owner, repo, candidate, ref, output, listing = sharedDocs)
                for (dir in listing.filter { it.optString("type") == "dir" }) {
                    if (skillRoots >= MAX_SKILL_ROOTS) break
                    val childPath = dir.optString("path")
                    require(validPath(childPath)) { "Invalid GitHub skill folder" }
                    val child = entries(owner, repo, childPath, ref) ?: continue
                    if (child.any { it.optString("type") == "file" && (
                        it.optString("name").equals("SKILL.md", true) ||
                            it.optString("name").endsWith(".md", true) ||
                            it.optString("name").endsWith(".mdc", true)
                        ) }) {
                        collectTree(owner, repo, childPath, ref, output, listing = child)
                        skillRoots++
                    }
                }
                // Some repositories use standalone .md skills without SKILL.md/frontmatter.
                if (listing.none { it.optString("type") == "dir" } &&
                    sharedDocs.any { it.optString("name").endsWith(".md", true) }) skillRoots++
            }
            if (skillRoots >= MAX_SKILL_ROOTS) break
        }
        // Fallback: if a repository has no dedicated skills directory, bundle root README/docs markdown so any GitHub repo can be imported as a skill!
        if (output.isEmpty()) {
            val fallbackRoot = root.filter { item ->
                val fn = item.optString("name").lowercase(Locale.ROOT)
                item.optString("type") == "file" && (fn.endsWith(".md") || fn.endsWith(".txt") || fn.endsWith(".mdc"))
            }
            if (fallbackRoot.isNotEmpty()) collectTree(owner, repo, "", ref, output, listing = fallbackRoot)
            val docsDir = root.firstOrNull { it.optString("type") == "dir" && it.optString("name").equals("docs", true) }
            if (docsDir != null) collectTree(owner, repo, docsDir.optString("path"), ref, output)
        }
        return output
    }

    private fun normalizeUserGitHubInput(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("github.com/", ignoreCase = true) ||
            cleaned.startsWith("raw.githubusercontent.com/", ignoreCase = true)
        ) {
            cleaned = "https://$cleaned"
        }
        // Strip browser fragments (#readme, #L10, etc.) and benign query parameters (?tab=...)
        cleaned = cleaned.substringBefore('#').trimEnd('/')
        if ('?' in cleaned) {
            val base = cleaned.substringBefore('?')
            val q = cleaned.substringAfter('?')
            cleaned = if (Regex("tab=[A-Za-z0-9_-]{1,48}").matches(q)) "$base?$q" else base
        }
        return cleaned
    }

    /**
     * A repository, folder or SKILL.md URL creates ONE user-named skill document including all
     * text files in its discovered skill folders (references, scripts, YAML, etc.). No partial saves.
     */
    suspend fun importFromGitHub(linkInput: String, nameInput: String): SavedAiSkill {
        val name = nameInput.trim().removePrefix("/")
        require(CustomSlashCommands.NAME.matches(name)) {
            "Enter a skill name: 1–48 letters/numbers, _ or -, starting with a letter/number"
        }
        require(!name.equals("plu", true) && CustomSlashCommands.find(name) == null &&
            SlashCommandsCache.getCachedSync().none { it.name.equals(name, true) }) {
            "/$name is already used by another command"
        }
        check(isCurrentAccount()) { "Sign in to SAIF AI before saving skills" }
        val accountKey = key ?: error("Sign in before saving skills")
        val accountUid = boundUid
        val source = GithubSkillLinks.parse(normalizeUserGitHubInput(linkInput))
        val sourceUrl: String
        val fallbackDescription: String
        val discovered: List<DiscoveredFile>
        when (source) {
            is GithubSkillSource.Repository -> {
                val (ref, description) = repoInfo(source.owner, source.repo)
                sourceUrl = "https://github.com/${source.owner}/${source.repo}"
                fallbackDescription = description
                discovered = repositoryFiles(source.owner, source.repo, ref)
            }
            is GithubSkillSource.Directory -> {
                val (_, description) = repoInfo(source.owner, source.repo)
                val (ref, path) = findDirectory(source.owner, source.repo, source.refAndPath)
                sourceUrl = "https://github.com/${source.owner}/${source.repo}/tree/${source.refAndPath.joinToString("/")}"
                fallbackDescription = description
                val output = mutableListOf<DiscoveredFile>()
                collectTree(source.owner, source.repo, path, ref, output)
                discovered = output
            }
            is GithubSkillSource.File -> {
                val (ref, path) = findFile(source.owner, source.repo, source.refAndPath)
                sourceUrl = source.rawUrl
                fallbackDescription = "Imported from ${source.owner}/${source.repo}"
                val output = mutableListOf<DiscoveredFile>()
                val parent = path.substringBeforeLast('/', "")
                val siblings = entries(source.owner, source.repo, parent, ref) ?: error("Skill folder not found")
                if (source.fileName.equals("SKILL.md", true) ||
                    siblings.any { it.optString("name").equals("SKILL.md", true) && it.optString("type") == "file" }) {
                    if (parent.isBlank()) collectRootSkill(source.owner, source.repo, ref, siblings, output)
                    else collectTree(source.owner, source.repo, parent, ref, output, listing = siblings)
                } else {
                    val raw = checkedRawUrl(source.rawUrl, source.owner, source.repo, path)
                    output.add(DiscoveredFile(path, raw, -1))
                }
                discovered = output
            }
        }
        val unique = discovered.distinctBy { it.path }.sortedWith(
            compareBy<DiscoveredFile> {
                val fn = it.path.substringAfterLast('/').lowercase(Locale.ROOT)
                when (fn) {
                    "skill.md" -> 0
                    "agents.md", "claude.md", "gemini.md" -> 1
                    "readme.md" -> 2
                    else -> 3
                }
            }.thenBy { it.path }
        )
        require(unique.isNotEmpty()) { "No skill files found. Paste a GitHub repository, skill folder, or Markdown link." }
        val textFiles = mutableListOf<SkillBundleFile>()
        val otherAssets = mutableListOf<String>()
        var accumulatedBytes = 0
        val maxContentBudget = SkillBundle.IMPORT_MAX_BYTES - 32 * 1024
        for (file in unique) {
            if (file.path.substringAfterLast('.', "").lowercase(Locale.ROOT) in binaryExtensions) {
                otherAssets.add(file.path)
                continue
            }
            if (file.size > MAX_SINGLE_FILE_BYTES) {
                otherAssets.add(file.path)
                continue
            }
            if (textFiles.size >= SkillBundle.IMPORT_MAX_FILES || accumulatedBytes >= maxContentBudget) {
                otherAssets.add(file.path)
                continue
            }
            val bytes = runCatching { fetchGitHubBytes(file.rawUrl) }.getOrNull()
            val decoded = bytes?.let { SkillBundle.decodeText(it) }
            if (decoded != null) {
                val fileBytes = decoded.toByteArray(Charsets.UTF_8).size
                val overhead = file.path.length * 2 + 64
                if (accumulatedBytes + fileBytes + overhead <= maxContentBudget) {
                    textFiles.add(SkillBundleFile(file.path, decoded))
                    accumulatedBytes += fileBytes + overhead
                } else {
                    val remainingChars = (maxContentBudget - accumulatedBytes - overhead - 256).coerceAtLeast(0)
                    if (remainingChars > 1024 && textFiles.isEmpty()) {
                        val clipped = decoded.take(remainingChars) + "\n\n[Truncated to fit SAIF AI skill bundle limit]"
                        textFiles.add(SkillBundleFile(file.path, clipped))
                        accumulatedBytes += clipped.toByteArray(Charsets.UTF_8).size + overhead
                    } else {
                        otherAssets.add(file.path)
                    }
                }
            } else {
                otherAssets.add(file.path)
            }
        }
        val skillCount = textFiles.count { it.path.substringAfterLast('/').equals("SKILL.md", true) }
        val firstSkill = textFiles.firstOrNull { it.path.substringAfterLast('/').equals("SKILL.md", true) }
            ?: textFiles.firstOrNull()
        val description = if (skillCount > 1) "$skillCount GitHub skills bundled · $fallbackDescription".take(500)
            else firstSkill?.let { GithubSkillMetadata.description(it.text, fallbackDescription) } ?: fallbackDescription
        val document = SkillBundle.compose(
            name = name,
            sourceUrl = sourceUrl,
            files = textFiles,
            otherAssets = otherAssets,
            maxBytes = SkillBundle.IMPORT_MAX_BYTES,
            maxFiles = SkillBundle.IMPORT_MAX_FILES
        )
        check(key == accountKey && boundUid == accountUid && isCurrentAccount()) {
            "Account changed while fetching. Please import the skill again."
        }
        val existing = find(name)
        require(existing == null || existing.sourceUrl == sourceUrl) {
            "/$name already belongs to another saved skill. Choose a different name."
        }
        require(existing != null || skills.size < MAX_SKILLS) { "Maximum $MAX_SKILLS saved skills; remove one before importing" }
        return SavedAiSkill(name, description, sourceUrl, document, System.currentTimeMillis()).also(::save)
    }

    fun save(skill: SavedAiSkill) {
        val accountKey = key ?: error("Sign in before saving skills")
        check(isCurrentAccount()) { "Sign in before saving skills" }
        require(CustomSlashCommands.NAME.matches(skill.name) && skill.instructions.isNotBlank()) { "Invalid skill" }
        require(find(skill.name)?.sourceUrl in listOf(null, skill.sourceUrl)) { "/${skill.name} belongs to another skill" }
        require(skills.size < MAX_SKILLS || find(skill.name) != null) { "Maximum $MAX_SKILLS skills reached" }
        writeFile(accountKey, skill.name, skill.instructions)
        skills.removeAll { it.name.equals(skill.name, true) }
        skills.add(0, skill)
        persist()
    }

    fun delete(name: String) {
        if (!isCurrentAccount()) return
        val accountKey = key ?: return
        val skill = find(name) ?: return
        AtomicFile(fileFor(accountKey, skill.name)).delete()
        skills.removeAll { it.name.equals(name, true) }
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        val storageKey = key ?: return
        val json = JSONArray(skills.map { skill ->
            JSONObject().put("name", skill.name).put("description", skill.description)
                .put("sourceUrl", skill.sourceUrl).put("savedAt", skill.savedAt).apply {
                    // Only if a legacy migration could not write to disk: keep its old content until
                    // it can be migrated. Normal imports store just metadata + one .md file.
                    if (!fileFor(storageKey, skill.name).isFile) put("instructions", skill.instructions)
                }
        })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(storageKey, json.toString()).apply()
    }
}
