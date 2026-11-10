/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.data.local.ChatSessionEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.SaifDatabase
import com.example.gemini.R
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SAIF AI", appName)
  }

  @Test
  fun `test database session creation`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = SaifDatabase.getDatabase(context)
    val dao = db.chatDao()

    val session = ChatSessionEntity(title = "Test Session", mode = "coding")
    dao.insertSession(session)

    val fetched = dao.getSessionById(session.id, ownerUid = "")
    assertNotNull(fetched)
    assertEquals("Test Session", fetched?.title)
    assertEquals("coding", fetched?.mode)
  }

  @Test
  fun `chat rows and bookmarks are scoped to their Google account`() = runBlocking {
    val dao = SaifDatabase.getDatabase(ApplicationProvider.getApplicationContext<Context>()).chatDao()
    val suffix = java.util.UUID.randomUUID().toString()
    val accountA = "account-a-$suffix"
    val accountB = "account-b-$suffix"
    val a = ChatSessionEntity(title = "Same name", ownerUid = accountA)
    val b = ChatSessionEntity(title = "Same name", ownerUid = accountB)
    dao.insertSession(a)
    dao.insertSession(b)
    dao.insertMessage(ChatMessageEntity(sessionId = a.id, role = "user", content = "Private to A", isBookmarked = true))
    dao.insertMessage(ChatMessageEntity(sessionId = b.id, role = "user", content = "Private to B", isBookmarked = true))

    assertEquals(listOf(a.id), dao.getAllSessions(accountA).first().map { it.id })
    assertEquals(listOf(b.id), dao.getAllSessions(accountB).first().map { it.id })
    assertEquals("Private to A", dao.getBookmarkedMessages(accountA).first().single().content)
    assertEquals("Private to B", dao.getBookmarkedMessages(accountB).first().single().content)
    assertEquals(emptyList<ChatMessageEntity>(), dao.getRecentMessages(a.id, accountB))
    dao.deleteAllSessions(accountB)
    assertNotNull(dao.getSessionById(a.id, accountA))
  }
}
