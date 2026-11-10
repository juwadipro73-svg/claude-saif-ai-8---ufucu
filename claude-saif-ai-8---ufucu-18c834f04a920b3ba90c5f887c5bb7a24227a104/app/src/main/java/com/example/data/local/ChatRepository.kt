/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.data.local

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** SAIF AI's device-local chat history is private to the active, verified app account owner in this app. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository(private val chatDao: ChatDao) {
    private fun ownerUid(): String? = AuthManager.currentUser.value?.let { user ->
        user.uid.takeIf { it.isNotBlank() && user.isActive && !user.isBanned }
    }

    private val account = AuthManager.currentUser.map { user ->
        user?.takeIf { it.uid.isNotBlank() && it.isActive && !it.isBanned }?.uid
    }.distinctUntilChanged()

    val allSessions: Flow<List<ChatSessionEntity>> = account.flatMapLatest { owner ->
        if (owner == null) flowOf(emptyList()) else chatDao.getAllSessions(owner)
    }
    val bookmarkedMessages: Flow<List<ChatMessageEntity>> = account.flatMapLatest { owner ->
        if (owner == null) flowOf(emptyList()) else chatDao.getBookmarkedMessages(owner)
    }

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> = account.flatMapLatest { owner ->
        if (owner == null) flowOf(emptyList()) else chatDao.getMessagesForSession(sessionId, owner)
    }

    suspend fun getRecentMessages(sessionId: String, limit: Int = 20): List<ChatMessageEntity> {
        val owner = ownerUid() ?: return emptyList()
        val messages = chatDao.getRecentMessages(sessionId, owner, limit)
        return if (ownerUid() == owner) messages else emptyList()
    }

    suspend fun getSessionById(sessionId: String): ChatSessionEntity? {
        val owner = ownerUid() ?: return null
        val session = chatDao.getSessionById(sessionId, owner)
        return session?.takeIf { ownerUid() == owner }
    }

    suspend fun createSession(title: String, mode: String = "general"): ChatSessionEntity {
        val owner = ownerUid() ?: error("Sign in to SAIF AI before starting a chat")
        val session = ChatSessionEntity(title = title, mode = mode, ownerUid = owner)
        chatDao.insertSession(session)
        return session
    }

    suspend fun updateSessionTitle(sessionId: String, newTitle: String) {
        ownerUid()?.let { chatDao.updateSessionTitle(sessionId, it, newTitle) }
    }

    suspend fun updateSessionTimestamp(sessionId: String) {
        ownerUid()?.let { chatDao.updateSessionTimestamp(sessionId, it) }
    }

    suspend fun deleteSession(sessionId: String) {
        ownerUid()?.let { chatDao.deleteSession(sessionId, it) }
    }

    suspend fun deleteAllSessions() {
        ownerUid()?.let { chatDao.deleteAllSessions(it) }
    }

    suspend fun insertMessage(message: ChatMessageEntity) {
        val owner = ownerUid() ?: error("Sign in to SAIF AI before saving a chat")
        check(chatDao.getSessionById(message.sessionId, owner) != null && ownerUid() == owner) {
            "Chat does not belong to the signed-in app account"
        }
        chatDao.insertMessage(message)
        chatDao.updateSessionTimestamp(message.sessionId, owner)
    }

    suspend fun updateMessageContent(messageId: String, content: String) {
        ownerUid()?.let { chatDao.updateMessageContent(messageId, it, content) }
    }

    suspend fun toggleBookmark(messageId: String, currentBookmarked: Boolean) {
        ownerUid()?.let { chatDao.setMessageBookmarked(messageId, it, !currentBookmarked) }
    }

    suspend fun deleteMessage(messageId: String) {
        ownerUid()?.let { chatDao.deleteMessage(messageId, it) }
    }

    suspend fun clearSessionMessages(sessionId: String) {
        ownerUid()?.let { chatDao.deleteMessagesForSession(sessionId, it) }
    }
}
