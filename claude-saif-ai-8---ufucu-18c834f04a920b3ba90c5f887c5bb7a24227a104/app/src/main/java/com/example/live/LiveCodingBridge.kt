/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.live

import com.example.gemini.data.remote.SavedAiSkills
import com.example.gemini.data.remote.SkillSlashParsing

/** Routes spoken coding/file requests to the SAME Antigravity chat backend as typed messages. */
object LiveCodingBridge {
    @Volatile var isReady: (() -> Boolean)? = null
    @Volatile var send: (suspend (String) -> String)? = null

    private val codingNoun = Regex("\\b(app|website|site|project|repo|repository|code|file|folder|feature|bug|build|design|script|apk|game|skill|command|database|server|ui)\\b", RegexOption.IGNORE_CASE)
    private val changeVerb = Regex("\\b(make|create|build|write|edit|change|fix|update|modify|add|remove|implement|banao|banado|bana|likho|badlo|sudhar|jodo|hatao|karo|banana)\\b", RegexOption.IGNORE_CASE)
    private val hindiCode = Regex("ऐप|गेम|वेबसाइट|फ़ाइल|फाइल|कोड|प्रोजेक्ट|स्क्रिप्ट|बग|सर्वर|कमांड|डिज़ाइन|रिपो")
    private val hindiAction = Regex("बनाओ|बना दो|लिखो|बदलो|ठीक करो|जोड़ो|हटाओ|सुधारो|तैयार करो")

    fun available(): Boolean = send != null && isReady?.invoke() == true

    fun shouldHandle(text: String): Boolean =
        SkillSlashParsing.parse(text)?.let { SavedAiSkills.find(it.command) } != null ||
            ((codingNoun.containsMatchIn(text) || hindiCode.containsMatchIn(text)) &&
                (changeVerb.containsMatchIn(text) || hindiAction.containsMatchIn(text)))

    suspend fun request(text: String): String =
        send?.invoke(text) ?: "Antigravity chat is not available. Open SAIF AI and reconnect the server."
}
