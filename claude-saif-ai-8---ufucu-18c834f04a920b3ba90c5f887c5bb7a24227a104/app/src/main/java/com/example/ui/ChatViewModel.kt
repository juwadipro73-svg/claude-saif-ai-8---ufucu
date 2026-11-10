/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatRepository
import com.example.data.local.ChatSessionEntity
import com.example.data.remote.GeminiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import android.net.Uri
import java.io.File
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val repository: ChatRepository,
    private val application: android.app.Application
) : ViewModel() {
    private val engine by lazy { com.example.gemini.ui.chat.ChatViewModelHolder.get(application) }
    private val mainChatStore = com.example.gemini.data.agent.ChatSessionStore()
    private val mainChatBackend by lazy {
        com.example.gemini.data.agent.agy.AgyChatBackend(
            application, viewModelScope, engine.authPreferences, mainChatStore,
            isNetworkConnectedProvider = { true }, onHubOnline = {},
            // Settings → "Auto approve": ON = run commands without asking, OFF = ask the user first (default).
            commandPolicyProvider = {
                if (com.example.ui.components.SaifEngineSettings.isAutoApprove(application)) "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER"
                else "CASCADE_COMMANDS_AUTO_EXECUTION_OFF"
            },
            authInfoProvider = { engine.agyAuthInfo.value }
        )
    }
    val antigravityModels get() = engine.agyBackend.availableModels
    val antigravitySelectedModel get() = engine.agyBackend.selectedModelId
    val antigravityConnection get() = engine.systemConnectionState
    val antigravityAuthInfo get() = engine.agyAuthInfo
    val antigravityQuotas get() = engine.agyBackend.quotas
    fun selectAntigravityModel(id: String) = engine.agyBackend.selectModel(id)
    suspend fun connectGoogleFlow(context: android.content.Context): String {
        val appContext = context.applicationContext
        com.example.gemini.data.remote.AndroidLocalBridgeServer.instance.start(appContext)
        val manager = com.example.gemini.ui.browser.BrowserExtensionManager.instance
        manager.init(appContext)
        manager.ensureHost()
        val extension = manager.loadExtensions().firstOrNull { it.id == "google-flow" && it.isEnabled }
            ?: error("Bundled Google Flow plugin is missing or disabled.")
        manager.tools(extension.id) // verify that the plugin host answers before writing configuration
        val raw = com.example.gemini.data.daemon.IdeApiClient.getMcpConfig()
            ?: error("Could not read existing MCP configuration. Start the local server and retry.")
        val config = org.json.JSONObject(raw)
        val servers = config.optJSONObject("mcpServers") ?: org.json.JSONObject().also { config.put("mcpServers", it) }
        val deviceId = android.provider.Settings.Secure.getString(appContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
        servers.put(manager.mcpServerName(extension.id), org.json.JSONObject()
            .put("serverUrl", manager.mcpUrl(extension.id))
            .put("headers", org.json.JSONObject().put("X-Device-Id", deviceId)))
        check(com.example.gemini.data.daemon.IdeApiClient.saveMcpConfig(config.toString(2))) { "Could not save Flow MCP configuration. Is the local server running?" }
        engine.agyBackend.agyHubClient.refreshMcpServers().getOrThrow()
        com.example.gemini.ui.browser.BrowserSessionManager.instance.openUrl("https://flow.google.com/", newTab = true)
        engine.requestViewMode("BROWSER")
        return "Flow MCP configured. Sign in to Google Flow in the in-app browser, then test the connection."
    }
    val mainAgentMessages get() = mainChatBackend.messages
    private val _mainAgentSessionId = MutableStateFlow<String?>(null)
    val mainAgentSessionId: StateFlow<String?> = _mainAgentSessionId.asStateFlow()
    private var sessionLoadJob: Job? = null
    fun terminateMainTool(tool: com.example.gemini.domain.model.ToolCall, id: String) = mainChatBackend.terminateRunningTerminalTool(tool, id)
    val mainAgentApprovals get() = mainChatBackend.pendingApprovals
    fun approveMainTool(tool: com.example.gemini.domain.model.ToolCall, messageId: String, scope: String?) = mainChatBackend.approveAndExecuteTerminalTool(tool, messageId, scope)
    fun rejectMainTool(tool: com.example.gemini.domain.model.ToolCall, messageId: String, reason: String?) = mainChatBackend.rejectTerminalTool(tool, messageId, reason)
    fun approveAllMainTools(items: List<com.example.gemini.ui.chat.PendingToolApproval>) = mainChatBackend.approveAllPendingTools(items)
    fun rejectAllMainTools(items: List<com.example.gemini.ui.chat.PendingToolApproval>) = mainChatBackend.rejectAllPendingTools(items)
    fun submitMainChoices(tool: com.example.gemini.domain.model.ToolCall, id: String, responses: List<com.example.gemini.data.remote.dto.AskQuestionResponseItemDto>, summary: String) = mainChatBackend.submitUserChoices(tool, id, responses, summary)
    fun skipMainChoices(tool: com.example.gemini.domain.model.ToolCall, id: String, responses: List<com.example.gemini.data.remote.dto.AskQuestionResponseItemDto>) = mainChatBackend.skipUserChoices(tool, id, responses)
    fun cancelMainChoices(tool: com.example.gemini.domain.model.ToolCall, id: String) = mainChatBackend.cancelUserChoices(tool, id)


    val allSessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedMessages: StateFlow<List<ChatMessageEntity>> = repository.bookmarkedMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _currentMode = MutableStateFlow("general")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _isFilterBookmarked = MutableStateFlow(false)
    val isFilterBookmarked: StateFlow<Boolean> = _isFilterBookmarked.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _pendingOpenVideoProject = MutableStateFlow<com.example.ui.components.VideoProjectData?>(null)
    val pendingOpenVideoProject: StateFlow<com.example.ui.components.VideoProjectData?> = _pendingOpenVideoProject.asStateFlow()

    fun consumePendingVideoProject() {
        _pendingOpenVideoProject.value = null
    }

    fun triggerVideoProjectOpen(project: com.example.ui.components.VideoProjectData) {
        _pendingOpenVideoProject.value = project
    }

    private fun isVideoCreationPrompt(prompt: String): Boolean {
        if (_currentMode.value == "video" || _currentMode.value == "video_project") return true
        val p = prompt.lowercase().trim()
        if (p.contains("grow on youtube") || p.contains("how to grow on youtube") || p.contains("grow channel")) return true
        if (p.contains("promt se video") || p.contains("prompt se video") || p.contains("prompt to video") || p.contains("script to video")) return true
        val videoKeywords = listOf(
            "video", "reel", "short", "clip", "movie", "animat", "timeline", "edl",
            "youtube", "render", "director", "editor", "sfx", "b-roll", "broll", "voiceover"
        )
        val actionKeywords = listOf(
            "bana", "create", "make", "generate", "render", "edit", "chahiye", "ready karo",
            "karo", "do", "produce", "synthesize", "likho", "write", "bna", "dikhao", "play"
        )
        val hasVideo = videoKeywords.any { p.contains(it) }
        val hasAction = actionKeywords.any { p.contains(it) }
        return hasVideo && (hasAction || p.split(Regex("\\s+")).size <= 6)
    }

    private val _selectedAttachments = MutableStateFlow<List<Uri>>(emptyList())
    val selectedAttachments: StateFlow<List<Uri>> = _selectedAttachments.asStateFlow()

    
    fun processAudioToText(context: android.content.Context, file: File) {
        engine.agyBackend.transcribeAudioFile(file, onDone = { text ->
            _input.value = listOf(_input.value, text).filter { it.isNotBlank() }.joinToString(" ")
            file.delete()
        }, onError = { message ->
            android.widget.Toast.makeText(context, "Antigravity transcription: $message", android.widget.Toast.LENGTH_LONG).show()
            file.delete()
        })
    }

    fun addAttachments(uris: List<Uri>) {
        _selectedAttachments.value = _selectedAttachments.value + uris
    }

    fun removeAttachment(uri: Uri) {
        _selectedAttachments.value = _selectedAttachments.value.filter { it != uri }
    }
    
    fun clearAttachments() {
        _selectedAttachments.value = emptyList()
    }

    private val _currentStreamingContent = MutableStateFlow("")
    val currentStreamingContent: StateFlow<String> = _currentStreamingContent.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _customSystemPrompt = MutableStateFlow("")
    val customSystemPrompt: StateFlow<String> = _customSystemPrompt.asStateFlow()

    private var activeGenerationJob: Job? = null

    // Observe messages for active session
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // This ViewModel survives sign-out and another Google sign-in in the same Activity.
        // Do not carry drafts, attachments, an in-flight reply or a selected chat across accounts.
        viewModelScope.launch {
            com.example.data.local.AuthManager.currentUser
                .map { it?.uid }.distinctUntilChanged().collect {
                    activeGenerationJob?.cancel()
                    sessionLoadJob?.cancel()
                    _activeSessionId.value = null
                    _mainAgentSessionId.value = null
                    _input.value = ""
                    _selectedAttachments.value = emptyList()
                    _currentStreamingContent.value = ""
                    _isGenerating.value = false
                }
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun setMode(mode: String) {
        _currentMode.value = mode
        val curId = _activeSessionId.value
        if (curId != null) {
            viewModelScope.launch {
                val session = repository.getSessionById(curId)
                if (session != null) {
                    repository.updateSessionTitle(curId, session.title)
                }
            }
        }
    }

    fun toggleBookmarkFilter() {
        _isFilterBookmarked.value = !_isFilterBookmarked.value
    }

    fun selectSession(sessionId: String) {
        if (_isGenerating.value) return
        _activeSessionId.value = sessionId
        _mainAgentSessionId.value = null
        sessionLoadJob?.cancel()
        sessionLoadJob = viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            if (session != null && _activeSessionId.value == sessionId) _currentMode.value = session.mode
            val cascade = application.getSharedPreferences("saif_main_cascades", android.content.Context.MODE_PRIVATE).getString(sessionId, null)
            if (!cascade.isNullOrBlank() && engine.systemConnectionState.value.isReady) {
                try {
                    if (mainChatBackend.currentConversation.value?.id != cascade) mainChatBackend.selectConversation(cascade)
                    kotlinx.coroutines.withTimeout(30_000L) {
                        while (mainChatBackend.currentConversation.value?.id != cascade || mainChatBackend.isLoadingConversation.value) kotlinx.coroutines.delay(100)
                    }
                    if (_activeSessionId.value == sessionId && mainChatBackend.conversationError.value == null) _mainAgentSessionId.value = sessionId
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                }
            }
        }
    }

    fun createNewSession(mode: String = _currentMode.value) {
        if (_isGenerating.value) return
        sessionLoadJob?.cancel()
        _mainAgentSessionId.value = null
        viewModelScope.launch {
            val newSession = repository.createSession(
                title = "New Chat",
                mode = mode
            )
            _activeSessionId.value = newSession.id
            _currentMode.value = mode
            _isFilterBookmarked.value = false
        }
    }

    fun createProjectSession(
        appName: String,
        packageName: String,
        minSdk: Int,
        targetSdk: Int,
        buildStudio: String,
        language: String,
        iconBitmap: android.graphics.Bitmap?,
        context: android.content.Context
    ) {
        viewModelScope.launch {
            // Save ProjectData for persistent lookup when reopened from history drawer
            val projData = com.example.ui.components.ProjectData(
                appName = appName,
                packageName = packageName,
                minSdk = minSdk,
                targetSdk = targetSdk,
                buildStudio = buildStudio,
                language = language,
                iconBitmap = iconBitmap
            )
            com.example.util.ProjectDataManager.saveProject(context, projData)

            val sessionTitle = appName.trim()
            val newSession = repository.createSession(
                title = sessionTitle,
                mode = "project"
            )
            _activeSessionId.value = newSession.id
            _currentMode.value = "coding"
            _isFilterBookmarked.value = false

            // Do not insert developer initialization message into chatDao so home screen chat stays clean!

            // Seed initial chat history for Build AI studio
            val welcomeBuildMsg = com.example.ui.components.BuildAiMessage(
                sender = "assistant",
                content = "Hello! I am SAIF AI, your coding specialist for **$appName**. Tell me what to build, modify, or fix, and I will write and auto-apply the changes directly to your files.",
                timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            )
            com.example.util.ProjectChatHistoryManager.saveMessages(context, appName, listOf(welcomeBuildMsg))
        }
    }

    fun createVideoProjectSession(
        videoProject: com.example.ui.components.VideoProjectData,
        context: android.content.Context
    ) {
        viewModelScope.launch {
            // Save video project persistently
            com.example.util.ProjectDataManager.saveVideoProject(context, videoProject)

            val sessionTitle = videoProject.projectName.trim().ifBlank { "Video Project" }
            val existing = allSessions.value.firstOrNull { it.title.equals(sessionTitle, ignoreCase = true) && (it.mode == "video_project" || it.mode == "project") }
            if (existing != null) {
                _activeSessionId.value = existing.id
            } else {
                val newSession = repository.createSession(
                    title = sessionTitle,
                    mode = "video_project"
                )
                _activeSessionId.value = newSession.id
            }
            _currentMode.value = "general"
            _isFilterBookmarked.value = false
        }
    }

    private fun safeUriToBase64(context: android.content.Context, uri: android.net.Uri, maxDim: Int = 1024): String? {
        return try {
            val boundsOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var sampleSize = 1
            var w = origWidth
            var h = origHeight
            while (w > maxDim * 2 || h > maxDim * 2) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val scale = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height, 1f)
            val finalBmp = if (scale < 1f) {
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                if (scaled != bmp) bmp.recycle()
                scaled
            } else {
                bmp
            }

            val out = java.io.ByteArrayOutputStream()
            finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            finalBmp.recycle()
            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            android.util.Log.e("ChatViewModel", "Failed to convert image to Base64: ${t.message}", t)
            null
        }
    }

    fun sendMessage(
        context: android.content.Context,
        userPrompt: String = _input.value,
        onSessionSelected: ((String) -> Unit)? = null
    ): Job? {
        val trimmed = userPrompt.trim()
        val attachments = _selectedAttachments.value.toList()
        if ((trimmed.isEmpty() && attachments.isEmpty()) || _isGenerating.value) return null

        // Never discard the draft when the shared hub needs setup or sign-in.
        val connection = engine.systemConnectionState.value
        if (!connection.isReady) {
            val message = when (connection.status) {
                com.example.gemini.data.remote.SystemStatus.UNAUTHENTICATED -> "Antigravity isn't connected. Use the Sign In button in chat to connect it."
                com.example.gemini.data.remote.SystemStatus.ERROR -> "SAIF AI server error: tap the status dot to reconnect."
                else -> "SAIF AI server isn't ready. Tap the status dot in the header to reconnect."
            }
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            return null
        }
        val accountIssue = com.example.gemini.data.remote.GoogleAccountBinding.denialReason(
            com.example.data.local.AuthManager.currentUser.value, engine.agyAuthInfo.value
        )
        if (accountIssue != null) {
            android.widget.Toast.makeText(context, accountIssue, android.widget.Toast.LENGTH_LONG).show()
            return null
        }
        _input.value = ""
        _selectedAttachments.value = emptyList()
        sessionLoadJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isGenerating.value = true
            var sessionId: String? = null
            try {
                com.example.gemini.data.remote.AndroidLocalBridgeServer.instance.ensureLinuxCliReady(context)
                com.example.github.GitHubIntegrationManager.init(context)

                sessionId = _activeSessionId.value
                if (sessionId == null) {
                    val title = trimmed.take(28).ifBlank { "Attachment" }
                    sessionId = repository.createSession(title, _currentMode.value).id
                    _activeSessionId.value = sessionId
                } else if (repository.getSessionById(sessionId)?.title == "New Chat") {
                    repository.updateSessionTitle(sessionId, trimmed.take(28).ifBlank { "Attachment" })
                }
                onSessionSelected?.invoke(sessionId)
                val history = repository.getRecentMessages(sessionId, 20)
                    .filter { it.content.isNotBlank() && (it.role == "user" || it.role == "model") }
                val prompt = trimmed.ifBlank { "Please inspect and describe the attached file in detail." }
                repository.insertMessage(ChatMessageEntity(
                    sessionId = sessionId, role = "user", content = trimmed.ifBlank { "Attachment" },
                    attachmentUris = attachments.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() }
                ))

                val model = engine.agyBackend.selectedModelId.value.ifBlank {
                    engine.agyBackend.availableModels.value.firstOrNull()?.id.orEmpty()
                }
                check(model.isNotBlank()) { "No Antigravity models available. Check sign-in and try again." }
                mainChatBackend.selectModel(model)
                val sessionPrefs = application.getSharedPreferences("saif_main_cascades", android.content.Context.MODE_PRIVATE)
                val savedCascade = sessionPrefs.getString(sessionId, null)
                val newCascade = savedCascade.isNullOrBlank()
                if (newCascade) {
                    mainChatBackend.startNewChat()
                    check(mainChatBackend.currentConversation.value != null) { "Unable to create Antigravity conversation." }
                } else {
                    if (mainChatBackend.currentConversation.value?.id != savedCascade) mainChatBackend.selectConversation(savedCascade!!)
                    kotlinx.coroutines.withTimeout(30_000L) {
                        while (mainChatBackend.currentConversation.value?.id != savedCascade || mainChatBackend.isLoadingConversation.value) {
                            kotlinx.coroutines.delay(100)
                        }
                    }
                    mainChatBackend.conversationError.value?.let { error(it) }
                }

                // Stage all attachments (including large mp4/apk/zip/pdf/images) into Linux /root/uploads/
                // and extract visual keyframes & metadata directly for Antigravity
                val stagedBundle = ChatAttachmentStager.prepareForAntigravity(context, attachments)
                mainChatStore.attachments.value = stagedBundle.inlineAttachments

                // A stable hub cascade is linked to each SAIF history entry. Older local-only chats get context once.
                val historyText = (if (newCascade) history else emptyList()).joinToString("\n\n") {
                    (if (it.role == "user") "User: " else "Assistant: ") + it.content.take(6000)
                }
                val systemText = _customSystemPrompt.value.trim()
                val request = buildString {
                    if (systemText.isNotBlank()) append("Instructions: ").append(systemText).append("\n\n")
                    append(DIRECT_ANSWER_RULE).append("\n\n")
                    val memory = com.example.util.SaifMemoryCore.promptBlock(com.example.util.SaifMemoryCore.Features.CHAT)
                    if (memory.isNotBlank()) append(memory).append("\n\n")
                    if (historyText.isNotBlank()) append("Previous conversation (for context):\n").append(historyText).append("\n\n")
                    if (stagedBundle.promptContextBlock.isNotBlank()) {
                        append(stagedBundle.promptContextBlock).append("\n\n")
                    }
                    append("Current user message:\n").append(com.example.gemini.data.remote.PromptExpansion.expand(prompt))
                }
                mainChatBackend.sendMessage(request)
                _mainAgentSessionId.value = sessionId
                val result = kotlinx.coroutines.withTimeout(1_800_000L) {
                    var stableSince = 0L
                    var lastFingerprint = 0
                    while (true) {
                        kotlinx.coroutines.delay(200)
                        val msgs = mainChatBackend.messages.value
                        val lastUserIndex = msgs.indexOfLast { it.role == com.example.gemini.domain.model.MessageRole.USER }
                        val assistant = msgs.drop(lastUserIndex + 1).lastOrNull { it.role == com.example.gemini.domain.model.MessageRole.ASSISTANT }
                        val text = assistant?.content.orEmpty()
                        if (text.isNotBlank()) {
                            require(com.example.cloud.CloudRequestFilter.shouldDisplayInline(text.toByteArray(Charsets.UTF_8).size)) {
                                "Reply exceeds 3 MB. Ask for a smaller text response or start a cloud job."
                            }
                            _currentStreamingContent.value = text
                        }
                        val turn = msgs.drop(lastUserIndex + 1)
                        val pending = turn.flatMap { it.toolCalls }.any { it.status == "PENDING_APPROVAL" || it.status == "RUNNING" }
                        val question = com.example.gemini.ui.agents.findPendingQuestion(com.example.gemini.domain.model.AgentKind.AGY, turn)
                        val cascadeId = mainChatBackend.currentConversation.value?.id
                        val summaries = engine.agyBackend.conversations.value
                        val relatedIds = mutableSetOf<String>()
                        cascadeId?.let { relatedIds.add(it) }
                        repeat(4) { summaries.filter { it.parentConversationId != null && it.parentConversationId in relatedIds }.forEach { relatedIds.add(it.id) } }
                        val busy = summaries.any { it.id in relatedIds && (it.isRunning || it.notFullyIdle) }
                        if (newCascade && turn.any { it.content.isNotBlank() || it.toolCalls.isNotEmpty() }) {
                            cascadeId?.let { sessionPrefs.edit().putString(sessionId, it).apply() }
                        }
                        if (!pending && question == null && !busy && assistant != null && text.isNotBlank() && !assistant.isStreaming && !mainChatBackend.isStreaming.value) {
                            val fingerprint = turn.hashCode()
                            if (fingerprint != lastFingerprint || stableSince == 0L) { lastFingerprint = fingerprint; stableSince = System.currentTimeMillis() }
                            // Allow the hub time to report delegated subagents before treating a reply as final.
                            // A plain chat answer (no tools, no subagents) is final almost at once, so "hello" is not slow.
                            val usedTools = turn.any { it.toolCalls.isNotEmpty() }
                            val settleMs = if (usedTools || relatedIds.size > 1) 10_000L else 1_500L
                            if (System.currentTimeMillis() - stableSince >= settleMs) return@withTimeout text
                        } else { stableSince = 0L; lastFingerprint = 0 }
                        mainChatBackend.conversationError.value?.let { error(it) }
                    }
                    @Suppress("UNREACHABLE_CODE") ""
                }
                if (newCascade) {
                    mainChatBackend.currentConversation.value?.id?.let { sessionPrefs.edit().putString(sessionId, it).apply() }
                }
                val finalDeliveredResult = if (result.contains("<saif-apk", ignoreCase = true)) {
                    val sessionTitle = allSessions.value.firstOrNull { it.id == sessionId }?.title
                    val compiledReply = runCatching {
                        com.example.compiler.OnDeviceApkBuildManager.processAssistantReplyForEmbeddedApk(
                            context = context,
                            userPrompt = trimmed,
                            assistantReply = result,
                            sessionTitle = sessionTitle
                        )
                    }.getOrDefault(result)
                    if (compiledReply != result) {
                        sessionPrefs.edit().remove(sessionId).apply()
                        _mainAgentSessionId.value = null
                    }
                    compiledReply
                } else {
                    result
                }
                repository.insertMessage(ChatMessageEntity(sessionId = sessionId, role = "model", content = finalDeliveredResult))
                com.example.util.SaifMemoryCore.learn(com.example.util.SaifMemoryCore.Features.CHAT, prompt, finalDeliveredResult)
                val prefs = context.getSharedPreferences("SettingsPrefs", android.content.Context.MODE_PRIVATE)
                if (prefs.getBoolean("autoSpeak", false)) com.example.utils.TTSManager.speak(finalDeliveredResult, context)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                    mainChatBackend.stopStreaming()
                    sessionId?.let { repository.insertMessage(ChatMessageEntity(sessionId = it, role = "model", content = "Antigravity response timed out. Please check the server and try again.")) }
                } else throw cancelled
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error.message?.startsWith("Chat attachments must total under 3 MB") == true) {
                    if (_input.value.isBlank()) _input.value = trimmed
                    if (_selectedAttachments.value.isEmpty()) _selectedAttachments.value = attachments
                }
                sessionId?.let { repository.insertMessage(ChatMessageEntity(sessionId = it, role = "model", content = "Antigravity error: ${error.localizedMessage ?: "Unknown error"}")) }
            } finally {
                _currentStreamingContent.value = ""
                _isGenerating.value = false
            }
        }
        return activeGenerationJob
    }

    /** Uses the same verified connection, attachments pipeline and chat history as a typed coding request. */
    suspend fun sendLiveCodingTask(context: android.content.Context, spokenRequest: String): String {
        if (_isGenerating.value) return "Antigravity is still working on the last request. Check the chat before sending another edit."
        if (!engine.systemConnectionState.value.isReady) return "Antigravity is not signed in or the server is still starting. Reconnect in SAIF AI first."
        com.example.gemini.data.remote.GoogleAccountBinding.denialReason(
            com.example.data.local.AuthManager.currentUser.value, engine.agyAuthInfo.value
        )?.let { return it }
        val selectedSession = kotlinx.coroutines.CompletableDeferred<String>()
        val job = sendMessage(context, spokenRequest, onSessionSelected = { selectedSession.complete(it) })
            ?: return "Could not send your coding request. Please check the chat connection."
        val sessionId = kotlinx.coroutines.withTimeoutOrNull(10_000L) { selectedSession.await() }
            ?: return "Could not open the Antigravity chat. Please check the connection."
        val finished = kotlinx.coroutines.withTimeoutOrNull(90_000L) { job.join(); true } ?: false
        if (!finished) return "Your request is running in Antigravity chat. You can review its progress and approve tools there."
        if (job.isCancelled) return "That Antigravity request was stopped. Check the chat for details."
        // Don't speak an older response, or a response from a chat the user switched to meanwhile.
        val turn = repository.getRecentMessages(sessionId, 2)
        val last = turn.lastOrNull()
        if (last?.role != "model" || turn.firstOrNull()?.role != "user" ||
            turn.firstOrNull()?.content != spokenRequest.trim()) {
            return "Your request is in chat. Check for pending tool approval or a reply."
        }
        return if (last.content.startsWith("Antigravity error:") || last.content.startsWith("Antigravity response timed out"))
            last.content else last.content.take(1500)
    }

    fun stopGeneration() {
        mainChatBackend.stopStreaming()
        activeGenerationJob?.cancel()
        val partial = _currentStreamingContent.value
        val sessionId = _activeSessionId.value
        if (partial.isNotBlank() && sessionId != null) {
            viewModelScope.launch {
                val partialMsg = ChatMessageEntity(
                    sessionId = sessionId,
                    role = "model",
                    content = "$partial [Stopped]"
                )
                repository.insertMessage(partialMsg)
            }
        }
        _currentStreamingContent.value = ""
        _isGenerating.value = false
    }

    fun regenerateLastResponse(context: android.content.Context) {
        if (_isGenerating.value) return
        val messages = activeMessages.value
        val lastUserMsg = messages.lastOrNull { it.role == "user" }
        if (lastUserMsg != null) {
            sendMessage(context, lastUserMsg.content)

        }
    }

    fun toggleBookmark(message: ChatMessageEntity) {
        viewModelScope.launch {
            repository.toggleBookmark(message.id, message.isBookmarked)
        }
    }

    fun renameSession(sessionId: String, newTitle: String, context: android.content.Context? = null) {
        viewModelScope.launch {
            val target = allSessions.value.firstOrNull { it.id == sessionId }
            if (target != null && context != null) {
                if (target.mode == "video_project" || com.example.util.ProjectDataManager.hasVideoProject(context, target.title)) {
                    val oldProj = com.example.util.ProjectDataManager.getVideoProject(context, target.title)
                    if (oldProj != null) {
                        com.example.util.ProjectDataManager.deleteVideoProject(context, target.title)
                        com.example.util.ProjectDataManager.saveVideoProject(context, oldProj.copy(projectName = newTitle.trim()))
                    }
                } else if (target.mode == "project" || com.example.util.ProjectDataManager.hasProject(context, target.title)) {
                    val oldProj = com.example.util.ProjectDataManager.getProject(context, target.title)
                    if (oldProj != null) {
                        com.example.util.ProjectDataManager.renameProject(context, target.title, newTitle.trim(), oldProj.copy(appName = newTitle.trim()))
                    }
                }
            }
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun deleteSession(sessionId: String, context: android.content.Context? = null) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            val target = allSessions.value.firstOrNull { it.id == sessionId }
            if (target != null && context != null) {
                if (target.mode == "project" || com.example.util.ProjectDataManager.hasProject(context, target.title)) {
                    com.example.util.ProjectDataManager.deleteProject(context, target.title)
                    com.example.util.ProjectChatHistoryManager.clearMessages(context, target.title)
                } else if (target.mode == "video_project" || com.example.util.ProjectDataManager.hasVideoProject(context, target.title)) {
                    com.example.util.ProjectDataManager.deleteVideoProject(context, target.title)
                }
            }
            repository.deleteSession(sessionId)
            application.getSharedPreferences("saif_main_cascades", android.content.Context.MODE_PRIVATE).edit().remove(sessionId).apply()
            if (_activeSessionId.value == sessionId) {
                val remaining = allSessions.value.filter { it.id != sessionId }
                _activeSessionId.value = remaining.firstOrNull()?.id
            }
        }
    }

    fun clearAllSessions() {
        if (_isGenerating.value) return
        viewModelScope.launch {
            repository.deleteAllSessions()
            application.getSharedPreferences("saif_main_cascades", android.content.Context.MODE_PRIVATE).edit().clear().apply()
            _activeSessionId.value = null
        }
    }

    fun clearCurrentSessionMessages() {
        if (_isGenerating.value) return
        val curId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.clearSessionMessages(curId)
            application.getSharedPreferences("saif_main_cascades", android.content.Context.MODE_PRIVATE).edit().remove(curId).apply()
        }
    }

    fun updateSettings(temp: Float, prompt: String) {
        _temperature.value = temp
        _customSystemPrompt.value = prompt
    }

    companion object {
        /** Keeps everyday chat fast while letting Antigravity build & compile real APKs in its Linux workspace. */
        const val DIRECT_ANSWER_RULE =
            "Reply rule: for greetings, small talk and simple daily-life or general-knowledge questions, answer directly " +
                "in plain text straight away and do NOT run any command, tool or subagent. Use commands and tools when " +
                "the task genuinely needs them (building or editing apps and files, code, git, research, media or video). " +
                "When the user attaches any file (video .mp4, .apk, .zip, image, code, etc.) and asks a question about it, " +
                "inspect the inline preview/keyframes/metadata and the staged file in `/root/uploads/` to answer accurately, " +
                "or use `/root/uploads/` directly in your commands if they ask you to process or build with it. " +
                "When the user asks you to create/build an Android app or APK in chat: create the project inside " +
                "`/root/workspace/<app-slug>/` using your normal workspace/terminal commands (either a rich multi-file " +
                "mobile HTML5/CSS/JS app with `index.html` or a native Java Android layout under `app/src/main/{AndroidManifest.xml,java,res}`), " +
                "then run `saif-build-apk /root/workspace/<app-slug> --name \"App Name\" --package com.saifai.app` in the terminal " +
                "to compile & cryptographically sign (v1/v2/v3) the APK, and include the printed `DOWNLOAD_URL` (`http://127.0.0.1:...`) " +
                "as a clickable Markdown link `[Download App APK](DOWNLOAD_URL)` in your reply. You can also publish any built " +
                "file/ZIP/MP4 in Linux into a clickable download link via `saif-apk-link /path/to/file`."
    }

    class Factory(private val repository: ChatRepository, private val application: android.app.Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(repository, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
