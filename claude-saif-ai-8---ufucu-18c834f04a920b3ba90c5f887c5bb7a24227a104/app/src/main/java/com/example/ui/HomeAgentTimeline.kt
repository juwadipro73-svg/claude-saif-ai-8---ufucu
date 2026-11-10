/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui

import com.example.data.local.ChatMessageEntity
import com.example.gemini.domain.model.ChatAttachment
import com.example.gemini.domain.model.ChatMessage
import com.example.gemini.domain.model.MessageRole

/** Keep older Room-only turns, but render mapped turns from the hub's authoritative trajectory. */
internal fun homeAgentTimeline(
    local: List<ChatMessageEntity>,
    agent: List<ChatMessage>
): List<ChatMessage> {
    fun localMessage(item: ChatMessageEntity): ChatMessage = ChatMessage(
        id = "local_${item.id}", conversationId = item.sessionId,
        role = if (item.role == "user") MessageRole.USER else MessageRole.ASSISTANT,
        content = item.content, createdAt = item.timestamp,
        attachments = item.attachmentUris.orEmpty().split(',').filter { it.isNotBlank() }.map {
            ChatAttachment(name = it.substringAfterLast('/'), path = "", localUri = it, isImage = true)
        }
    )
    val localUsers = local.withIndex().filter { it.value.role == "user" }
    val agentUsers = agent.count { it.role == MessageRole.USER }
    if (agentUsers == 0 || localUsers.size < agentUsers) return local.map(::localMessage)
    val matchingUsers = localUsers.takeLast(agentUsers)
    val prefix = local.take(matchingUsers.first().index).map(::localMessage)
    var userIndex = 0
    val nativeTurns = agent.map { message ->
        if (message.role == MessageRole.USER) {
            val original = matchingUsers[userIndex++].value
            // The backend prompt may contain hidden memory or migrated history; show only what was typed.
            message.copy(content = original.content, attachments = message.attachments.ifEmpty { localMessage(original).attachments })
        } else message
    }
    // A transport error is saved locally rather than being a trajectory step.
    val error = local.lastOrNull()?.takeIf { it.role == "model" && (it.content.startsWith("Antigravity error:") || it.content.startsWith("Antigravity response timed out.")) }
    return prefix + nativeTurns + listOfNotNull(error?.let(::localMessage))
}
