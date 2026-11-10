/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.jarvis.assistant.live

import com.jarvis.assistant.background.JarvisLiveError
import com.jarvis.assistant.data.model.ConversationState

/** Implemented by the host app; the automation library must not depend on the app module. */
interface HostLiveTurnLoop {
    fun start(): Boolean
    fun stop()
    fun setMicMuted(muted: Boolean)
    fun interrupt()
    fun sendText(text: String)
    fun onScreenFrame(jpeg: ByteArray?)
}

interface HostLiveTurnListener {
    fun onState(state: ConversationState)
    fun onUserTranscript(text: String)
    fun onAssistantTranscript(text: String)
    fun onAudioLevel(level: Float)
    fun onActivity()
    fun onTurnCompleted(user: String, assistant: String)
    fun onError(error: JarvisLiveError)
}
