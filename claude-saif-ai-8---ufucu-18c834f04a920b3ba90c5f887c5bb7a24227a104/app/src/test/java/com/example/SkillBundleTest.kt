/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import com.example.gemini.data.remote.GithubSkillLinks
import com.example.gemini.data.remote.GithubSkillSource
import com.example.gemini.data.remote.SkillBundle
import com.example.gemini.data.remote.SkillBundleFile
import org.junit.Assert.*
import org.junit.Test

class SkillBundleTest {
    @Test fun repositoryFilesMakeOneNamedDocumentWithAllTextAndRelativePaths() {
        val files = listOf(
            SkillBundleFile(".agents/skills/research/references/guide.md", "# Guide\nSee the detailed flow"),
            SkillBundleFile(".agents/skills/research/agents/openai.yaml", "interface:\n  description: Test"),
            SkillBundleFile(".agents/skills/research/SKILL.md", "---\nname: research\n---\nRead references/guide.md"),
            SkillBundleFile(".agents/skills/qa/SKILL.md", "# QA steps")
        )
        val document = SkillBundle.compose("mera-skill", "https://github.com/acme/tools", files,
            otherAssets = listOf(".agents/skills/qa/icon.png"))
        assertTrue(document.startsWith("# SAIF AI skill bundle: /mera-skill"))
        assertEquals(4, Regex("===== BEGIN FILE: ").findAll(document).count())
        assertTrue(document.indexOf("research/SKILL.md") < document.indexOf("research/references/guide.md"))
        assertTrue(document.contains("Read references/guide.md"))
        assertTrue(document.contains("See the detailed flow"))
        assertTrue(document.contains("interface:\n  description: Test"))
        assertTrue(document.contains("qa/SKILL.md"))
        assertTrue(document.contains("qa/icon.png"))
        assertTrue(document.contains("bytes not included in the chat prompt"))
    }

    @Test fun overLimitIsRejectedRatherThanSavingAPartialSkill() {
        val oversized = listOf(SkillBundleFile("SKILL.md", "a".repeat(SkillBundle.MAX_BYTES)))
        val outcome = runCatching { SkillBundle.compose("my-skill", "https://github.com/acme/repo", oversized) }
        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull()?.message.orEmpty().contains("256 KB"))
        assertTrue(runCatching {
            SkillBundle.compose("my-skill", "https://github.com/acme/repo",
                List(SkillBundle.MAX_FILES + 1) { SkillBundleFile("$it.md", "ok") })
        }.isFailure)
    }

    @Test fun onlyValidUtf8TextIsEmbeddedInChatPrompt() {
        assertEquals("# Heading", SkillBundle.decodeText("\uFEFF# Heading".toByteArray(Charsets.UTF_8)))
        assertNull(SkillBundle.decodeText(byteArrayOf(0x00, 0x02, 0x03)))
        assertNull(SkillBundle.decodeText(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test fun skillFileLinksRetainOwnerAndBranchPathForSiblingDiscovery() {
        val file = GithubSkillLinks.parse("https://github.com/acme/project/blob/feature/voice/skills/chat/SKILL.md")
            as GithubSkillSource.File
        assertEquals("acme", file.owner)
        assertEquals("project", file.repo)
        assertEquals(listOf("feature", "voice", "skills", "chat", "SKILL.md"), file.refAndPath)
        assertEquals("https://raw.githubusercontent.com/acme/project/feature/voice/skills/chat/SKILL.md", file.rawUrl)
    }
}
