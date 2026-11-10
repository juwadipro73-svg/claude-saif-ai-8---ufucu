/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.agent.skills

import android.content.Context
import com.example.agent.AgentListener
import com.example.util.SaifAccessibilityService
import org.json.JSONObject

data class SkillResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

data class AgentEnv(
    val context: Context,
    val service: SaifAccessibilityService?,
    val listener: AgentListener?
)

interface Skill {
    val name: String
    suspend fun run(args: JSONObject, env: AgentEnv): SkillResult
}
