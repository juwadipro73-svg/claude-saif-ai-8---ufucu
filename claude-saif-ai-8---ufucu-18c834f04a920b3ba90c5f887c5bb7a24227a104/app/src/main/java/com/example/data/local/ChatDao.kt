/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** All reads and mutations of chat history carry the verified Firebase account's UID. */
@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions WHERE ownerUid = :ownerUid ORDER BY updatedAt DESC")
    fun getAllSessions(ownerUid: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId AND ownerUid = :ownerUid LIMIT 1")
    suspend fun getSessionById(sessionId: String, ownerUid: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE title = :title AND ownerUid = :ownerUid LIMIT 1")
    suspend fun getSessionByTitle(title: String, ownerUid: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :newTitle, updatedAt = :updatedAt WHERE id = :sessionId AND ownerUid = :ownerUid")
    suspend fun updateSessionTitle(sessionId: String, ownerUid: String, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :sessionId AND ownerUid = :ownerUid")
    suspend fun updateSessionTimestamp(sessionId: String, ownerUid: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId AND ownerUid = :ownerUid")
    suspend fun deleteSession(sessionId: String, ownerUid: String)

    @Query("DELETE FROM chat_sessions WHERE ownerUid = :ownerUid")
    suspend fun deleteAllSessions(ownerUid: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND sessionId IN (SELECT id FROM chat_sessions WHERE ownerUid = :ownerUid) ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String, ownerUid: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM chat_messages WHERE sessionId = :sessionId AND sessionId IN (SELECT id FROM chat_sessions WHERE ownerUid = :ownerUid) ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    suspend fun getRecentMessages(sessionId: String, ownerUid: String, limit: Int = 20): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET content = :content WHERE id = :messageId AND sessionId IN (SELECT id FROM chat_sessions WHERE ownerUid = :ownerUid)")
    suspend fun updateMessageContent(messageId: String, ownerUid: String, content: String)

    @Query("UPDATE chat_messages SET isBookmarked = :isBookmarked WHERE id = :messageId AND sessionId IN (SELECT id FROM chat_sessions WHERE ownerUid = :ownerUid)")
    suspend fun setMessageBookmarked(messageId: String, ownerUid: String, isBookmarked: Boolean)

    @Query("DELETE FROM chat_messages WHERE id = :messageId AND sessionId IN (SELECT id FROM chat_sessions WHERE ownerUid = :ownerUid)")
    suspend fun deleteMessage(messageId: String, ownerUid: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND sessionId IN (SELECT id FROM chat_sessions WHERE ownerUid = :ownerUid)")
    suspend fun deleteMessagesForSession(sessionId: String, ownerUid: String)

    @Query("SELECT chat_messages.* FROM chat_messages INNER JOIN chat_sessions ON chat_sessions.id = chat_messages.sessionId WHERE chat_messages.isBookmarked = 1 AND chat_sessions.ownerUid = :ownerUid ORDER BY chat_messages.timestamp DESC")
    fun getBookmarkedMessages(ownerUid: String): Flow<List<ChatMessageEntity>>
}
