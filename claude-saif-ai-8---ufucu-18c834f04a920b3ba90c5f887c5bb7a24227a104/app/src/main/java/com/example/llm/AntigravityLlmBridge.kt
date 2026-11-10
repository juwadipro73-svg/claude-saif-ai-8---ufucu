/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.llm

import android.app.Application
import com.example.data.local.AuthManager
import com.example.gemini.data.agent.ChatSessionStore
import com.example.gemini.data.agent.agy.AgyChatBackend
import com.example.gemini.data.remote.GoogleAccountBinding
import com.example.gemini.domain.model.ChatAttachment
import com.example.gemini.domain.model.MessageRole
import com.example.gemini.ui.chat.ChatViewModelHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Bridges LlmGateway / BuildAgent / OnDeviceApkBuildManager calls to the signed-in
 * Antigravity engine whenever the user has not configured a separate manual API key.
 */
object AntigravityLlmBridge {

    @Volatile
    private var appRef: Application? = null

    fun init(app: Application) {
        appRef = app
    }

    fun isAvailable(): Boolean {
        val app = appRef ?: return false
        return runCatching {
            val engine = ChatViewModelHolder.get(app)
            engine.systemConnectionState.value.isReady &&
                GoogleAccountBinding.denialReason(AuthManager.currentUser.value, engine.agyAuthInfo.value) == null
        }.getOrDefault(false)
    }

    suspend fun callIfAvailable(
        request: LlmRequest,
        preferredModel: String = "",
        onChunk: suspend (String) -> Unit = {},
        onThinking: (suspend (String) -> Unit)? = null
    ): Result<LlmResponse>? {
        val app = appRef ?: return null
        val engine = runCatching { ChatViewModelHolder.get(app) }.getOrNull() ?: return null
        if (!engine.systemConnectionState.value.isReady) return null
        val accountIssue = GoogleAccountBinding.denialReason(AuthManager.currentUser.value, engine.agyAuthInfo.value)
        if (accountIssue != null) return null

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ChatSessionStore()
        val backend = AgyChatBackend(
            application = app,
            backendScope = scope,
            authPrefs = engine.authPreferences,
            store = store,
            isNetworkConnectedProvider = { true },
            onHubOnline = {},
            commandPolicyProvider = { "CASCADE_COMMANDS_AUTO_EXECUTION_OFF" },
            authInfoProvider = { engine.agyAuthInfo.value }
        )

        return try {
            val activeModel = preferredModel.takeIf { modelId ->
                modelId.isNotBlank() && engine.agyBackend.availableModels.value.any { it.id == modelId || it.key == modelId }
            } ?: engine.agyBackend.selectedModelId.value.ifBlank {
                engine.agyBackend.availableModels.value.firstOrNull()?.id.orEmpty()
            }
            if (activeModel.isBlank()) return null

            val combinedPrompt = buildString {
                append("IMPORTANT EXECUTION RULE: Reply directly with the requested output in your message text ONLY. ")
                append("Do NOT execute terminal commands, file tools, browser tools, or subagents.\n\n")
                if (request.systemPrompt.isNotBlank()) {
                    append("System Instructions:\n")
                    append(request.systemPrompt.trim())
                    append("\n\n")
                }
                if (request.history.isNotEmpty()) {
                    append("Conversation Context:\n")
                    request.history.takeLast(10).forEach { (role, text) ->
                        val label = if (role.equals("user", true)) "User" else "Assistant"
                        append("$label: ${text.take(4000)}\n\n")
                    }
                }
                append(request.prompt)
            }

            if (request.imagesBase64.isNotEmpty()) {
                store.attachments.value = request.imagesBase64.mapIndexed { idx, b64 ->
                    val cleanB64 = b64.substringAfter("base64,", b64)
                    ChatAttachment(
                        name = "image_${idx + 1}.png",
                        path = "",
                        localUri = "",
                        isImage = true,
                        isAudio = false,
                        size = (cleanB64.length * 3L) / 4L,
                        mimeType = "image/png",
                        base64 = cleanB64
                    )
                }
            }

            withContext(Dispatchers.Main) {
                backend.selectModel(activeModel)
                backend.startNewChat()
                check(backend.currentConversation.value != null) { "Could not initialize Antigravity generation session." }
                backend.sendMessage(combinedPrompt)
            }

            var streamedLen = 0
            var thoughtLen = 0
            val finalOutput = withTimeout(300_000L) {
                var stableSince = 0L
                var lastFingerprint = 0
                while (true) {
                    delay(180)
                    backend.conversationError.value?.let { err ->
                        error(err)
                    }
                    val msgs = backend.messages.value
                    val lastUserIdx = msgs.indexOfLast { it.role == MessageRole.USER }
                    val turn = if (lastUserIdx >= 0) msgs.drop(lastUserIdx + 1) else emptyList()
                    val assistant = turn.lastOrNull { it.role == MessageRole.ASSISTANT }

                    // Auto-reject any accidental tool request so one-shot generation never hangs waiting for UI approval
                    val pendingTools = turn.flatMap { it.toolCalls.map { tc -> tc to it.id } }
                        .filter { (tc, _) -> tc.status == "PENDING_APPROVAL" }
                    for ((tc, msgId) in pendingTools) {
                        runCatching {
                            backend.rejectTerminalTool(tc, msgId, "Return the requested code/text directly in your reply without tools.")
                        }
                    }

                    val thought = assistant?.thoughtText.orEmpty()
                    if (onThinking != null && thought.length > thoughtLen) {
                        onThinking(thought.substring(thoughtLen))
                        thoughtLen = thought.length
                    }

                    val text = assistant?.content.orEmpty()
                    if (text.length > streamedLen) {
                        onChunk(text.substring(streamedLen))
                        streamedLen = text.length
                    }

                    val runningTools = turn.flatMap { it.toolCalls }.any { it.status == "PENDING_APPROVAL" || it.status == "RUNNING" }
                    if (!runningTools && assistant != null && text.isNotBlank() && !assistant.isStreaming && !backend.isStreaming.value) {
                        val fp = text.hashCode()
                        if (fp != lastFingerprint || stableSince == 0L) {
                            lastFingerprint = fp
                            stableSince = System.currentTimeMillis()
                        }
                        if (System.currentTimeMillis() - stableSince >= 1_200L) {
                            return@withTimeout text
                        }
                    } else {
                        stableSince = 0L
                        lastFingerprint = 0
                    }
                }
                @Suppress("UNREACHABLE_CODE") ""
            }

            Result.success(
                LlmResponse(
                    text = finalOutput,
                    finishReason = "STOP",
                    thinkingText = backend.messages.value.lastOrNull { it.role == MessageRole.ASSISTANT }?.thoughtText
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { backend.stopStreaming() }
            scope.cancel()
        }
    }
}
