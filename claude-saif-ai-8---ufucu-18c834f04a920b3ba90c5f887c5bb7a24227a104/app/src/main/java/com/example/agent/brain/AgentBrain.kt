/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.agent.brain

import com.example.agent.AgentCall
import com.example.agent.AgentOutcome
import com.example.agent.ScreenSnapshot
import com.example.agent.StepRecord

data class BrainDecision(
    val calls: List<AgentCall>,
    val thought: String = "",
    val finished: Boolean = false,
    val outcome: AgentOutcome? = null
)

interface AgentBrain {
    suspend fun decide(
        task: String,
        sessionCtx: String,
        notes: String,
        history: List<StepRecord>,
        snapshot: ScreenSnapshot,
        thinkingLevel: String = "low"
    ): BrainDecision
}

object AgentBrainFactory {
    private var useFallbackBrain = false

    fun markComputerUseFailed() {
        useFallbackBrain = true
    }

    fun isFallback(): Boolean = useFallbackBrain

    fun create(): AgentBrain {
        return if (useFallbackBrain) {
            FunctionCallingBrain
        } else {
            ComputerUseBrain
        }
    }
}
