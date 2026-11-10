/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.compiler

import android.content.Context
import com.example.builder.AutonomousAppBuilder
import com.example.data.local.ProviderSettingsManager
import com.example.gemini.data.remote.AndroidLocalBridgeServer
import com.example.llm.LlmGateway
import com.example.llm.LlmRequest
import com.example.llm.LlmRole
import com.example.ui.components.ProjectData
import com.example.util.ProjectDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class LocalApkBuildState(
    val id: String,
    val kind: String, // "APK" or "ZIP"
    val appName: String,
    val packageName: String,
    val status: String, // "building" | "compiling" | "ready" | "failed"
    val message: String,
    val progress: Float = 0f,
    val artifactFile: File? = null,
    val downloadsFile: File? = null,
    val directDownloadUrl: String? = null,
    val sizeBytes: Long = 0L
)

/**
 * End-to-end On-Device APK & Project ZIP Builder for SAIF AI Chat, Voice, and Studio.
 * Generates complete mobile apps, compiles & signs real standalone Android APKs (v1/v2/v3)
 * directly on the user's phone, saves to Downloads, and serves a direct HTTP download link —
 * zero external Cloud Run / GCS / Drive dependencies required.
 */
object OnDeviceApkBuildManager {

    private val _current = MutableStateFlow<LocalApkBuildState?>(null)
    val current: StateFlow<LocalApkBuildState?> = _current.asStateFlow()

    private val saifApkBlockRegex = Regex(
        """<saif-apk(?:\s+[^>]*)?>([\s\S]*?)</saif-apk>""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val nameAttrRegex = Regex("""name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val pkgAttrRegex = Regex("""package\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private val fillerWords = setOf(
        "a", "an", "the", "my", "me", "for", "of", "to", "in", "on", "with", "and", "or",
        "build", "compile", "package", "assemble", "create", "make", "generate", "export", "produce",
        "give", "send", "provide", "share", "get", "want", "need", "please", "now", "direct",
        "download", "link", "url", "file", "apk", "android", "app", "application", "installable",
        "mobile", "phone", "device", "local", "locally", "standalone", "signed", "debug", "release",
        "mujhe", "mujhko", "mere", "meri", "mera", "apne", "iske", "iska", "iski", "uske", "uska",
        "is", "iss", "ye", "yeh", "wo", "woh", "ek", "ko", "ka", "ki", "ke", "se", "par", "pe",
        "me", "mein", "hi", "bhi", "na", "to", "toh", "aur", "ya", "are", "arre", "bhai", "yar",
        "yaar", "sir", "tum", "aap", "ap", "banao", "banado", "banakar", "bana", "kar", "karke",
        "karo", "do", "de", "dedo", "dijiye", "chahiye", "compile", "install", "vala", "wala", "wali"
    )

    fun dismiss() {
        _current.value = null
    }

    fun recordReadyApk(
        context: Context,
        project: ProjectData,
        apkFile: File,
        downloadsFile: File? = null
    ): LocalApkBuildState {
        val cleanFileName = project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').ifBlank { "App" } + ".apk"
        val directUrl = runCatching {
            AndroidLocalBridgeServer.instance.registerDownloadFile(context, apkFile, cleanFileName)
        }.getOrNull()
        val state = LocalApkBuildState(
            id = UUID.randomUUID().toString(),
            kind = "APK",
            appName = project.appName,
            packageName = AppCompilerEngine.sanitizePackageName(project.packageName, project.appName),
            status = "ready",
            message = "${project.appName}.apk compiled & signed on your phone (${formatSize(apkFile.length())})",
            progress = 1f,
            artifactFile = apkFile,
            downloadsFile = downloadsFile,
            directDownloadUrl = directUrl,
            sizeBytes = apkFile.length()
        )
        _current.value = state
        return state
    }

    /**
     * Infers a clean, human-readable app title from the user's prompt or recent conversation context.
     */
    fun inferAppName(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        sessionTitle: String? = null
    ): String {
        // 1. Explicit quoted name in prompt
        val quoted = Regex("""["“']([A-Za-z0-9][A-Za-z0-9 _-]{1,28})["”']""").find(prompt)?.groupValues?.getOrNull(1)?.trim()
        if (!quoted.isNullOrBlank() && extractTopicWords(quoted).isNotEmpty()) {
            return titleCaseWords(extractTopicWords(quoted).take(4))
        }

        // 2. Meaningful topic words directly in the prompt
        val promptWords = extractTopicWords(prompt)
        if (promptWords.isNotEmpty()) {
            return titleCaseWords(promptWords.take(4))
        }

        // 3. Follow-up request (e.g. "mujhe iske apk ka direct download link do"): inspect recent history
        for ((role, msg) in history.asReversed()) {
            val htmlTitle = Regex("""<title>([^<]{2,40})</title>""", RegexOption.IGNORE_CASE)
                .find(msg)?.groupValues?.getOrNull(1)?.trim()
            if (!htmlTitle.isNullOrBlank() && !htmlTitle.equals("SAIF AI App", true)) {
                return htmlTitle.take(30)
            }
            // Known app domain phrases in assistant/user turns
            val knownMatch = Regex(
                """(?i)\b(temp(?:orary)?\s*mail|disposable\s*mail|calculator|todo(?:\s*list)?|notes?|stopwatch|timer|weather|expense\s*tracker|habit\s*tracker|qr\s*(?:scanner|generator)|password\s*(?:manager|generator)|tic\s*tac\s*toe|snake\s*game|chess|2048|flappy\s*bird|music\s*player|voice\s*recorder|flashlight|torch|unit\s*converter|bmi\s*calculator|pomodoro|quiz|chat\s*app)\b"""
            ).find(msg)?.groupValues?.getOrNull(1)
            if (!knownMatch.isNullOrBlank()) {
                return titleCaseWords(knownMatch.trim().split(Regex("\\s+")).take(3))
            }
            if (role.equals("user", true)) {
                val histWords = extractTopicWords(msg)
                if (histWords.isNotEmpty()) {
                    return titleCaseWords(histWords.take(4))
                }
            }
        }

        // 4. Session title fallback
        if (!sessionTitle.isNullOrBlank() && !sessionTitle.equals("New Chat", true)) {
            val titleWords = extractTopicWords(sessionTitle)
            if (titleWords.isNotEmpty()) {
                return titleCaseWords(titleWords.take(4))
            }
        }

        return "SAIF Smart App"
    }

    private fun extractTopicWords(text: String): List<String> {
        return text
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim('-') }
            .filter { token ->
                token.length >= 2 && token.lowercase() !in fillerWords
            }
    }

    private fun titleCaseWords(words: List<String>): String {
        return words.joinToString(" ") { w ->
            if (w.all { it.isUpperCase() || it.isDigit() } && w.length <= 5) w
            else w.lowercase().replaceFirstChar { ch -> ch.titlecase() }
        }.ifBlank { "SAIF Smart App" }
    }

    /**
     * Builds, compiles, signs, and stages a standalone Android APK directly on-device from a chat request.
     */
    suspend fun buildFromChat(
        context: Context,
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        sessionTitle: String? = null,
        imagesBase64: List<String> = emptyList()
    ): Pair<LocalApkBuildState, String> = withContext(Dispatchers.IO) {
        val buildId = UUID.randomUUID().toString()
        val appName = inferAppName(prompt, history, sessionTitle)
        val packageName = AppCompilerEngine.sanitizePackageName("", appName)
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').ifBlank { "App" }
        val projectDir = File(context.filesDir, "projects/$sanitized").apply { mkdirs() }

        _current.value = LocalApkBuildState(
            id = buildId,
            kind = "APK",
            appName = appName,
            packageName = packageName,
            status = "building",
            message = "1/3 Designing & coding $appName on your phone…",
            progress = 0.15f
        )

        try {
            // Check if user only asked for the download link of an already-built HTML app in history or workspace
            val pureLinkFollowUp = isPureLinkOrCompileFollowUp(prompt)
            val existingHistoryHtml = history.asReversed().firstNotNullOfOrNull { (_, content) ->
                extractSaifApkOrHtml(content)
            }
            val existingDiskHtml = File(projectDir, "index.html")
                .takeIf { pureLinkFollowUp && it.isFile && it.length() > 200 }
                ?.readText()

            val finalHtml = when {
                pureLinkFollowUp && !existingHistoryHtml.isNullOrBlank() -> existingHistoryHtml
                pureLinkFollowUp && !existingDiskHtml.isNullOrBlank() -> existingDiskHtml
                else -> generateCompleteMobileAppHtml(
                    appName = appName,
                    prompt = prompt,
                    history = history,
                    imagesBase64 = imagesBase64,
                    onStreamProgress = { chars ->
                        _current.value = _current.value?.copy(
                            message = "1/3 Generating $appName application code (${chars / 1024} KB)…",
                            progress = (0.18f + (chars.coerceAtMost(28_000) / 28_000f) * 0.42f)
                        )
                    }
                )
            }

            // Persist project files & Studio metadata
            val project = ProjectData(
                appName = appName,
                packageName = packageName,
                minSdk = 24,
                targetSdk = 34,
                buildStudio = "SAIF AI Studio",
                language = "Java",
                iconBitmap = ProjectDataManager.loadProjectIcon(context, appName)
            )
            AutonomousAppBuilder.ensureAndroidContainerStubs(projectDir, project)
            File(projectDir, "index.html").writeText(finalHtml)
            File(projectDir, "app/src/main/assets").apply { mkdirs() }
                .let { File(it, "index.html").writeText(finalHtml) }
            ProjectDataManager.saveProject(context, project)

            return@withContext compileAndPublishProject(context, buildId, project)
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: e.message ?: "APK generation failed"
            val failed = LocalApkBuildState(
                id = buildId,
                kind = "APK",
                appName = appName,
                packageName = packageName,
                status = "failed",
                message = errMsg,
                progress = 0f
            )
            _current.value = failed
            throw e
        }
    }

    /**
     * Detects `<saif-apk>` blocks emitted by normal chat turns, compiles them into a real signed APK,
     * and appends clickable download + install links to the assistant reply.
     */
    suspend fun processAssistantReplyForEmbeddedApk(
        context: Context,
        userPrompt: String,
        assistantReply: String,
        sessionTitle: String? = null
    ): String = withContext(Dispatchers.IO) {
        val match = saifApkBlockRegex.find(assistantReply) ?: return@withContext assistantReply
        val fullTag = match.value
        val headerPart = fullTag.substringBefore(">")
        val rawBody = match.groupValues[1]
        val html = AutonomousAppBuilder.extractHtmlDocument(rawBody)
            ?: AutonomousAppBuilder.healTruncatedHtml(rawBody)
            ?: return@withContext assistantReply

        val explicitName = nameAttrRegex.find(headerPart)?.groupValues?.getOrNull(1)?.trim()
        val appName = explicitName?.takeIf { it.isNotBlank() } ?: inferAppName(userPrompt, emptyList(), sessionTitle)
        val explicitPkg = pkgAttrRegex.find(headerPart)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val packageName = AppCompilerEngine.sanitizePackageName(explicitPkg, appName)
        val sanitized = appName.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').ifBlank { "App" }
        val projectDir = File(context.filesDir, "projects/$sanitized").apply { mkdirs() }

        val project = ProjectData(
            appName = appName,
            packageName = packageName,
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "SAIF AI Studio",
            language = "Java",
            iconBitmap = ProjectDataManager.loadProjectIcon(context, appName)
        )
        AutonomousAppBuilder.ensureAndroidContainerStubs(projectDir, project)
        File(projectDir, "index.html").writeText(html)
        File(projectDir, "app/src/main/assets").apply { mkdirs() }
            .let { File(it, "index.html").writeText(html) }
        ProjectDataManager.saveProject(context, project)

        val (_, summaryMarkdown) = compileAndPublishProject(context, UUID.randomUUID().toString(), project)
        val cleanedReply = assistantReply.replace(fullTag, "").trim()
        if (cleanedReply.isBlank()) summaryMarkdown else "$cleanedReply\n\n---\n$summaryMarkdown"
    }

    /**
     * Exports a project's Android Studio ZIP archive locally and returns a direct download link.
     */
    suspend fun exportProjectZipFromChat(
        context: Context,
        prompt: String,
        sessionTitle: String? = null
    ): Pair<LocalApkBuildState, String> = withContext(Dispatchers.IO) {
        val buildId = UUID.randomUUID().toString()
        val projectsRoot = File(context.filesDir, "projects")
        val targetDir = projectsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.lastModified() }

        val appName = targetDir?.name?.replace('_', ' ') ?: inferAppName(prompt, emptyList(), sessionTitle)
        val packageName = AppCompilerEngine.sanitizePackageName("", appName)
        val project = ProjectDataManager.getProject(context, appName) ?: ProjectData(
            appName = appName,
            packageName = packageName,
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "SAIF AI Studio",
            language = "Java",
            iconBitmap = null
        )
        ProjectDataManager.saveProject(context, project)

        val zipFile = AppCompilerEngine.exportProjectAsZip(context, project)
            ?: error("Could not package project ZIP archive.")
        val cleanZipName = "${appName.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').ifBlank { "Project" }}_Source.zip"
        val directUrl = AndroidLocalBridgeServer.instance.registerDownloadFile(context, zipFile, cleanZipName)
        val state = LocalApkBuildState(
            id = buildId,
            kind = "ZIP",
            appName = appName,
            packageName = project.packageName,
            status = "ready",
            message = "$cleanZipName ready (${formatSize(zipFile.length())})",
            progress = 1f,
            artifactFile = zipFile,
            downloadsFile = zipFile,
            directDownloadUrl = directUrl,
            sizeBytes = zipFile.length()
        )
        _current.value = state

        val md = buildString {
            append("### 📦 Project Source ZIP Ready: **$appName**\n\n")
            append("- **Direct Download Link:** [$cleanZipName]($directUrl)\n")
            append("- **Open Locally:** [Open $cleanZipName](file://${zipFile.absolutePath})\n")
            append("- **Size:** ${formatSize(zipFile.length())}")
        }
        state to md
    }

    private suspend fun compileAndPublishProject(
        context: Context,
        buildId: String,
        project: ProjectData
    ): Pair<LocalApkBuildState, String> {
        val cleanPkg = AppCompilerEngine.sanitizePackageName(project.packageName, project.appName)
        val normalizedProject = project.copy(packageName = cleanPkg)

        _current.value = LocalApkBuildState(
            id = buildId,
            kind = "APK",
            appName = normalizedProject.appName,
            packageName = cleanPkg,
            status = "compiling",
            message = "2/3 Compiling & signing ${normalizedProject.appName}.apk on phone…",
            progress = 0.68f
        )

        val compileResult = AppCompilerEngine.compileProject(
            context = context,
            project = normalizedProject,
            onStepChanged = { step, stepProg ->
                _current.value = _current.value?.copy(
                    status = "compiling",
                    message = "2/3 ${step.title}…",
                    progress = 0.65f + (stepProg * 0.30f)
                )
            },
            onLogAdded = {}
        )

        when (compileResult) {
            is BuildResult.Error -> {
                error("APK compiler error (${compileResult.errorStep.title}): ${compileResult.message}")
            }
            is BuildResult.Success -> {
                val apkFile = compileResult.apkFile
                val savedDownloads = AppCompilerEngine.saveApkToDownloads(
                    context = context,
                    apkFile = apkFile,
                    appName = normalizedProject.appName,
                    showToast = false
                )
                val cleanApkName = normalizedProject.appName
                    .replace(Regex("[^a-zA-Z0-9_]"), "_")
                    .trim('_')
                    .ifBlank { "App" } + ".apk"
                val directUrl = AndroidLocalBridgeServer.instance.registerDownloadFile(
                    context = context,
                    file = apkFile,
                    desiredFileName = cleanApkName
                )
                val sizeStr = formatSize(apkFile.length())
                val readyState = LocalApkBuildState(
                    id = buildId,
                    kind = "APK",
                    appName = normalizedProject.appName,
                    packageName = cleanPkg,
                    status = "ready",
                    message = "${normalizedProject.appName}.apk ready ($sizeStr) · Signed v1/v2/v3",
                    progress = 1f,
                    artifactFile = apkFile,
                    downloadsFile = savedDownloads,
                    directDownloadUrl = directUrl,
                    sizeBytes = apkFile.length()
                )
                _current.value = readyState

                val markdown = buildString {
                    append("### ✅ **${normalizedProject.appName}** APK Ready!\n\n")
                    append("Aapka Android APK phone par hi successfully compile aur cryptographically sign (`v1/v2/v3`) ho gaya hai.\n\n")
                    append("- 📥 **Direct Download Link:** [Download $cleanApkName ($sizeStr)]($directUrl)\n")
                    append("- 📲 **One-Tap Install:** [Install ${normalizedProject.appName} on Phone](file://${apkFile.absolutePath})\n")
                    append("- 📁 **Saved in Phone Downloads:** `Downloads/$cleanApkName`\n")
                    append("- 📦 **Package ID:** `$cleanPkg`\n\n")
                    append("*(Upar diye gaye card se bhi aap seedha **Install APK**, **Download**, **Copy Link**, ya **Share** kar sakte hain.)*")
                }
                return readyState to markdown
            }
        }
    }

    private suspend fun generateCompleteMobileAppHtml(
        appName: String,
        prompt: String,
        history: List<Pair<String, String>>,
        imagesBase64: List<String>,
        onStreamProgress: (Int) -> Unit
    ): String {
        val settings = ProviderSettingsManager.loadState()
        val provider = settings.providerName.ifBlank { "Gemini" }
        val apiKey = settings.apiKeys.firstOrNull { it.isNotBlank() && !it.contains("MY_") }.orEmpty()
        val model = settings.buildModel.ifBlank { settings.activeModel }

        val isTempMail = prompt.contains("temp", true) && prompt.contains("mail", true) ||
            appName.contains("temp", true) && appName.contains("mail", true) ||
            history.any { (_, m) -> m.contains("temp", true) && m.contains("mail", true) }

        val domainGuidance = if (isTempMail) {
            """
            DOMAIN NOTE (TEMPORARY MAIL APP):
            - Connect to the real public Mail.tm REST API (`https://api.mail.tm/domains`, `POST https://api.mail.tm/accounts`, `POST https://api.mail.tm/token`, `GET https://api.mail.tm/messages`, `GET https://api.mail.tm/messages/{id}`) with zero API key needed!
            - Include: instant disposable email generation, 1-tap Copy Email (`navigator.clipboard.writeText`), New Address / Delete Inbox button, Auto-Refresh countdown + Manual Refresh, Inbox message list, full HTML/text email reader modal, AND a seamless smart offline/fallback mode if network is unreachable so every button always works smoothly!
            """.trimIndent()
        } else ""

        val systemInstructions = buildString {
            append(AutonomousAppBuilder.BUILDER_SYSTEM_PROMPT)
            append("\n\nANDROID NATIVE CONTAINER CAPABILITIES:\n")
            append("- Runs inside SAIF AI's standalone Android WebView APK with INTERNET permission, localStorage, navigator.clipboard.writeText(), navigator.share(), and navigator.vibrate() enabled.\n")
            append("- Output ONLY the complete raw <!DOCTYPE html>...</html> document. NEVER say you cannot generate or host an APK.\n")
            if (domainGuidance.isNotBlank()) {
                append("\n").append(domainGuidance)
            }
        }

        val userBuildPrompt = buildString {
            append("Build the complete, production-ready mobile Android application named \"$appName\".\n")
            if (history.isNotEmpty()) {
                append("\nRecent conversation context (implement all features discussed here):\n")
                history.takeLast(6).forEach { (role, msg) ->
                    val speaker = if (role.equals("user", true)) "User" else "Assistant"
                    append("$speaker: ${msg.take(2500)}\n")
                }
            }
            append("\nCurrent user request: $prompt\n")
            append("\nReturn ONLY the complete <!DOCTYPE html>...</html> code starting with <!DOCTYPE html> and ending with </html>.")
        }

        var accumulated = 0
        val firstAttempt = LlmGateway.call(
            role = LlmRole.CODER,
            provider = provider,
            apiKey = apiKey,
            model = model,
            request = LlmRequest(
                prompt = userBuildPrompt,
                systemPrompt = systemInstructions,
                imagesBase64 = imagesBase64,
                mode = "coding",
                maxTokens = 12_000
            ),
            onChunk = { chunk ->
                accumulated += chunk.length
                onStreamProgress(accumulated)
            }
        )

        val rawText = firstAttempt.getOrElse { err ->
            throw IllegalStateException(
                "AI engine connection required to generate app code (${err.localizedMessage ?: "not connected"}). " +
                    "Connect Antigravity (green dot in top bar) or add an API key in Settings, then try again.",
                err
            )
        }.text

        extractSaifApkOrHtml(rawText)?.let { return ensureSaifBuildMarker(it) }

        // Recovery pass if the model replied with prose instead of raw HTML
        val retryAttempt = LlmGateway.call(
            role = LlmRole.CODER,
            provider = provider,
            apiKey = apiKey,
            model = model,
            request = LlmRequest(
                prompt = "Output ONLY the raw <!DOCTYPE html>...</html> single-file mobile app for \"$appName\" ($prompt). Start immediately with <!DOCTYPE html> and end with </html>. Zero explanation.",
                systemPrompt = AutonomousAppBuilder.BUILDER_SYSTEM_PROMPT,
                mode = "coding",
                maxTokens = 10_000
            )
        )
        val retryText = retryAttempt.getOrNull()?.text.orEmpty()
        extractSaifApkOrHtml(retryText)?.let { return ensureSaifBuildMarker(it) }

        throw IllegalStateException("The AI model did not return valid app HTML. Please retry.")
    }

    private fun extractSaifApkOrHtml(text: String): String? {
        if (text.isBlank()) return null
        val tagBody = saifApkBlockRegex.find(text)?.groupValues?.getOrNull(1)
        val target = tagBody ?: text
        val stripped = target
            .replace(Regex("```(?:html)?", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
        return AutonomousAppBuilder.extractHtmlDocument(stripped)
            ?: AutonomousAppBuilder.healTruncatedHtml(stripped)
    }

    private fun ensureSaifBuildMarker(html: String): String {
        if (html.contains("<!-- saif-ai-build -->", ignoreCase = true)) return html
        val headRegex = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
        val match = headRegex.find(html)
        return if (match != null) {
            html.substring(0, match.range.last + 1) + "\n<!-- saif-ai-build -->" + html.substring(match.range.last + 1)
        } else {
            "<!-- saif-ai-build -->\n$html"
        }
    }

    private fun isPureLinkOrCompileFollowUp(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val referencesPrevious = Regex("\\b(iske|iska|iski|is|iss|this|that|it|same|uska)\\b").containsMatchIn(lower)
        val asksLinkOrCompile = Regex("\\b(link|download|compile|apk)\\b|लिंक|डाउनलोड|एपीके").containsMatchIn(lower)
        val asksFeatureEdit = Regex("\\b(add|change|modify|update|redesign|dark|light|feature|screen|fix)\\b|बदलो|जोड़ो").containsMatchIn(lower)
        return referencesPrevious && asksLinkOrCompile && !asksFeatureEdit
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}
