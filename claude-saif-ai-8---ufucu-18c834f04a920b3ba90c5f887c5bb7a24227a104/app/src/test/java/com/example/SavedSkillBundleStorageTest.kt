/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AuthManager
import com.example.gemini.data.remote.PromptExpansion
import com.example.gemini.data.remote.SavedAiSkill
import com.example.gemini.data.remote.SavedAiSkills
import com.example.gemini.data.remote.SkillBundle
import com.example.gemini.data.remote.SkillBundleFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SavedSkillBundleStorageTest {
    @Test fun onePrivateMarkdownFilePersistsPerAccountAndExpandsInChat() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AuthManager.init(app)
        val firstUser = AuthManager.signInAsGuest()
        val bundle = SkillBundle.compose("my-research", "https://github.com/acme/repo", listOf(
            SkillBundleFile("skills/my/SKILL.md", "Use the included references."),
            SkillBundleFile("skills/my/references/steps.md", "Step 1: check the evidence.")
        ))
        SavedAiSkills.save(SavedAiSkill("my-research", "Research helper", "https://github.com/acme/repo", bundle, 100L))

        val folder = app.filesDir.resolve("saved-ai-skills")
        assertEquals(1, folder.walkTopDown().count { it.isFile && it.extension == "md" })
        val storedMetadata = app.getSharedPreferences("saif_saved_ai_skills", 0).all.values.joinToString()
        assertFalse("Don't duplicate the document in preferences", storedMetadata.contains("check the evidence"))
        val expanded = PromptExpansion.expand("/my-research find a source")
        assertTrue(expanded.contains("Step 1: check the evidence."))
        assertTrue(expanded.contains("Use the included references."))
        assertEquals("/my-research find a source", PromptExpansion.collapse(expanded))

        SavedAiSkills.bind(app, null)
        assertNull(SavedAiSkills.find("my-research"))
        SavedAiSkills.bind(app, firstUser.uid)
        assertEquals(bundle, SavedAiSkills.find("my-research")?.instructions)

        AuthManager.signInAsGuest()
        assertNull("Another signed-in owner cannot use this skill", SavedAiSkills.find("my-research"))
        assertEquals(1, folder.walkTopDown().count { it.isFile && it.extension == "md" })
    }
}
