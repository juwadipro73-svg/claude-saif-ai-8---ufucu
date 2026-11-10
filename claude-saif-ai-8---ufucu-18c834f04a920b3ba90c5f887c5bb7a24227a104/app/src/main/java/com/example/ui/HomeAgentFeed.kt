/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import com.example.gemini.ui.chat.ChatFeedCache
import com.example.gemini.ui.chat.ChatFeedItem
import com.example.gemini.ui.components.*
import com.example.gemini.domain.model.ChatMessage
import com.example.gemini.domain.model.ToolType

/** Uses the same parsed blocks, tool cards and working animation as the editing engine. */
@Composable
internal fun HomeAgentFeed(
    messages: List<ChatMessage>, modelId: String, generating: Boolean,
    listState: LazyListState, viewModel: ChatViewModel, onRetry: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val externalUris = LocalUriHandler.current
    val fileHandler = remember(context, scope) {
        FileLinkHandler(onOpenFile = { raw ->
            scope.launch {
                try {
                    val path = android.net.Uri.decode(raw).removePrefix("file://").substringBefore("#")
                    val file = withContext(Dispatchers.IO) {
                        val source = com.example.compiler.LinuxApkBuildBridge.resolveLinuxPathOnHost(context, path)
                            ?: java.io.File(path)
                        val name = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "output" }
                        val target = java.io.File(context.cacheDir, "agent-outputs").apply { mkdirs() }.let { java.io.File(it, name) }
                        if (source.isFile && source.canRead()) source.copyTo(target, overwrite = true) else {
                            val encoded = com.example.gemini.data.remote.AgyHubClient().readFileAsBase64("file://$path").getOrThrow()
                            require(encoded.length <= 200_000_000) { "File is too large to open here." }
                            target.writeBytes(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
                        }
                        target
                    }
                    if (file.extension.equals("apk", ignoreCase = true)) {
                        com.example.compiler.AppCompilerEngine.installApk(context, file)
                        return@launch
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val ext = file.extension.lowercase()
                    val type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, type)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(android.content.Intent.createChooser(intent, "Open ${file.name}"))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    val safeUris = remember(externalUris, fileHandler) {
        object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) {
                if (uri.startsWith("file://", true) || uri.startsWith("/")) fileHandler.onOpenFile(uri)
                else try { externalUris.openUri(uri) } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Cannot open link: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val feed = remember(messages, modelId) { ChatFeedCache.buildFeedItems(messages, modelId) }
    val question = remember(messages) { com.example.gemini.ui.agents.findPendingQuestion(com.example.gemini.domain.model.AgentKind.AGY, messages) }
    val approvals by viewModel.mainAgentApprovals.collectAsState()
    LaunchedEffect(feed.size, messages.lastOrNull(), generating) {
        val layout = listState.layoutInfo
        val nearBottom = layout.totalItemsCount == 0 || (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 3
        if (nearBottom && (feed.isNotEmpty() || generating)) {
            withFrameNanos { }
            val count = listState.layoutInfo.totalItemsCount
            if (count > 0) listState.scrollToItem(count - 1)
        }
    }
    CompositionLocalProvider(LocalQuestionPanelActive provides true, LocalFileLinkHandler provides fileHandler, LocalUriHandler provides safeUris) {
        LazyColumn(state = listState, contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
            items(feed, key = { it.key }, contentType = { it.contentType }) { item ->
                Box(Modifier.animateItem(fadeInSpec = tween(260), placementSpec = null, fadeOutSpec = null)) {
                when (item) {
                    is ChatFeedItem.User -> UserMessageBubble(message = item.message)
                    is ChatFeedItem.AssistantBlock -> Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                        val block: @Composable () -> Unit = {
                            MarkdownBlockView(
                                block = item.block,
                                onApproveTool = { viewModel.approveMainTool(it, item.messageId, null) },
                                onRejectTool = { if (it.toolType == ToolType.ASK_CHOICE) viewModel.cancelMainChoices(it, item.messageId) else viewModel.rejectMainTool(it, item.messageId, null) },
                                onTerminateTool = { viewModel.terminateMainTool(it, item.messageId) },
                                onSubmitChoices = { tool, responses, summary -> viewModel.submitMainChoices(tool, item.messageId, responses, summary) },
                                onSkipChoices = { tool, responses -> viewModel.skipMainChoices(tool, item.messageId, responses) }
                            )
                        }
                        when (item.block) {
                            is MarkdownBlock.Paragraph, is MarkdownBlock.Header, is MarkdownBlock.Bullet,
                            is MarkdownBlock.Numbered, is MarkdownBlock.Task, is MarkdownBlock.Blockquote,
                            is MarkdownBlock.Alert, is MarkdownBlock.Table -> SelectionContainer { block() }
                            else -> block()
                        }
                    }
                    is ChatFeedItem.AssistantThinking -> ThinkingAccordion(item.thoughtText, item.durationMs, item.isStreaming, modifier = Modifier.padding(horizontal = 16.dp))
                    is ChatFeedItem.AssistantTyping -> if (!generating) ModelTypingIndicator(modelId = item.modelId)
                    is ChatFeedItem.AssistantFooter -> AssistantMessageFooter(message = item.message, onRetry = { onRetry() })
                    is ChatFeedItem.StreamingMessage -> com.example.gemini.ui.components.MessageBubble(message = item.message, modelId = modelId)
                    is ChatFeedItem.Summary -> com.example.gemini.ui.components.MessageBubble(message = item.message, modelId = modelId)
                    is ChatFeedItem.Divider -> WaveDivider(label = item.label)
                }
                }
            }
            if (generating && approvals.isEmpty() && question == null) {
                item(key = "home_agent_working") { AgentWorkingSpinner(color = AgyAccent) }
            }
        }
    }
}
