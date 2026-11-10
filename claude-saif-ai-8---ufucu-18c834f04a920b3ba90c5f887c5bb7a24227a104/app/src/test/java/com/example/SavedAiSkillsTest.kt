/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import com.example.gemini.data.remote.GithubSkillLinks
import com.example.gemini.data.remote.GithubSkillMetadata
import com.example.gemini.data.remote.GithubSkillSource
import com.example.gemini.data.remote.SavedAiSkills
import com.example.gemini.data.remote.SkillSlashParsing
import org.junit.Assert.*
import org.junit.Test

class SavedAiSkillsTest {
    @Test fun leadingCommandKeepsTheSeparateUserTask() {
        val invocation = SkillSlashParsing.parse(" /coder ek snake game banao")
        assertEquals("coder", invocation?.command)
        assertEquals("ek snake game banao", invocation?.task)
        assertNull(SkillSlashParsing.parse("Please use /coder for this"))
        assertNull(SkillSlashParsing.parse("/coderExtra! not a command"))
        assertEquals("", SkillSlashParsing.query("/"))
        assertEquals("coder", SkillSlashParsing.query("/coder"))
        assertNull(SkillSlashParsing.query("/coder ek game"))
    }

    @Test fun normalChromeRepositoryAndFolderLinksResolveWithoutRawUrls() {
        assertEquals(GithubSkillSource.Repository("morluto", "rea"),
            GithubSkillLinks.parse("https://github.com/morluto/rea"))
        assertEquals(GithubSkillSource.Repository("morluto", "rea"),
            GithubSkillLinks.parse("https://github.com/morluto/rea.git?tab=readme-ov-file"))
        assertEquals(GithubSkillSource.Directory("morluto", "rea", listOf("main", ".agents", "skills")),
            GithubSkillLinks.parse("https://github.com/morluto/rea/tree/main/.agents/skills"))
        assertEquals("https://raw.githubusercontent.com/morluto/rea/main/.agents/skills/reverse-engineer-anything/SKILL.md",
            GithubSkillLinks.rawFileUrl("https://github.com/morluto/rea/blob/main/.agents/skills/reverse-engineer-anything/SKILL.md"))
        val rules = "---\nname: Reverse Engineer Anything\ndescription: Reverse engineer programs with agents\n---\n# Steps"
        assertEquals("reverse-engineer-anything", GithubSkillMetadata.name(rules, "rea"))
        assertEquals("Reverse engineer programs with agents", GithubSkillMetadata.description(rules, "Fallback"))
        assertEquals("my-skill", GithubSkillMetadata.name("# instructions", "my-skill"))
    }

    @Test fun githubRawLinksAreCanonicalAndNeverFetchArbitraryHosts() {
        assertEquals("https://raw.githubusercontent.com/acme/project/main/SKILL.md",
            SavedAiSkills.githubRawUrl("https://github.com/acme/project/blob/main/SKILL.md"))
        assertEquals("https://raw.githubusercontent.com/acme/project/main/SKILL.md",
            SavedAiSkills.githubRawUrl("https://raw.githubusercontent.com/acme/project/main/SKILL.md"))
        for (link in listOf(
            "http://github.com/acme/project/blob/main/SKILL.md",
            "https://evil.example/acme/project/main/SKILL.md",
            "https://github.com@evil.example/acme/project/main/SKILL.md",
            "https://github.com/acme/project/blob/main/SKILL.md?url=https://localhost/",
            "https://github.com/acme/project/blob/main/../../secret"
        )) {
            assertTrue("Should reject $link", runCatching { SavedAiSkills.githubRawUrl(link) }.isFailure)
        }
    }
}
