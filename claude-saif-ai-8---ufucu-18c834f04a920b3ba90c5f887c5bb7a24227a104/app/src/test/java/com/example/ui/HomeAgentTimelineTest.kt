/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui

import com.example.data.local.ChatMessageEntity
import com.example.gemini.domain.model.ChatMessage
import com.example.gemini.domain.model.MessageRole
import com.example.gemini.domain.model.ToolCall
import com.example.gemini.domain.model.ToolType
import org.junit.Assert.*
import org.junit.Test

class HomeAgentTimelineTest {
    private fun local(role: String, text: String) = ChatMessageEntity(sessionId = "s", role = role, content = text)
    private fun agentMessage(role: MessageRole, text: String) = ChatMessage(conversationId = "c", role = role, content = text)

    @Test fun nativeTrajectoryPreservesToolsAndHidesPromptContext() {
        val local = listOf(local("user", "draw a car"), local("model", "placeholder"))
        val tool = ToolCall(id = "t", command = "generate", toolType = ToolType.GENERATE_IMAGE, status = "SUCCESS")
        val agent = listOf(agentMessage(MessageRole.USER, "Instructions: secret\nCurrent user message:\ndraw a car"),
            agentMessage(MessageRole.ASSISTANT, "<!-- tool_call:t -->\n![car](file:///root/car.png)").copy(toolCalls = listOf(tool)))
        val result = homeAgentTimeline(local, agent)
        assertEquals(2, result.size)
        assertEquals("draw a car", result[0].content)
        assertEquals(tool, result[1].toolCalls.single())
        assertTrue(result[1].content.contains("car.png"))
    }

    @Test fun keepsLegacyTurnsBeforeNativeConversation() {
        val local = listOf(local("user", "old"), local("model", "old reply"), local("user", "new"), local("model", "new reply"))
        val agent = listOf(agentMessage(MessageRole.USER, "expanded new"), agentMessage(MessageRole.ASSISTANT, "new reply"))
        val result = homeAgentTimeline(local, agent)
        assertEquals(listOf("old", "old reply", "new", "new reply"), result.map { it.content })
    }

    @Test fun unavailableTrajectoryFallsBackToLocalHistory() {
        val local = listOf(local("user", "hello"), local("model", "hi"))
        assertEquals(listOf("hello", "hi"), homeAgentTimeline(local, emptyList()).map { it.content })
    }

    @Test fun localTransportErrorRemainsVisible() {
        val local = listOf(local("user", "hello"), local("model", "Antigravity error: offline"))
        val agent = listOf(agentMessage(MessageRole.USER, "hello"), agentMessage(MessageRole.ASSISTANT, "working"))
        assertEquals("Antigravity error: offline", homeAgentTimeline(local, agent).last().content)
    }
}
