/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import android.app.Application
import com.example.data.local.ProviderSettingsManager
import com.jarvis.assistant.SaifApiConfig
import com.jarvis.assistant.SaifAutomation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SaifApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProviderSettingsManager.init(this)

        // Round 10: pre-load the on-device speech model at app start, so the purple-mic live baatcheet
        // works with ANY provider key. Cloud STT exists only for Groq/OpenAI/Gemini keys; for every
        // other provider (OpenRouter, Anthropic, Groq-chat-only, custom...) Vosk transcribes on-device.
        try {
            com.jarvis.assistant.audio.VoskModelManager.init(
                this, CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
        } catch (_: Throwable) {}

        // Round 9: the memory recall of SAIF AI AUTOMATION is now an app-wide backend. Every feature (chat, live
        // baatcheet, video director, builder, agent) learns into it and recalls from it.
        com.example.util.SaifMemoryCore.init(this)
        com.example.utils.TTSManager.init(this)

        // SAIF ADVANCE EDITING ENGINE needs these at process start (the workspace has its own Application class that no
        // longer runs now that SaifApplication is the app class). The app self-updater and the original project's
        // analytics are deliberately NOT started here.
        try { com.example.gemini.data.adb.AdbConnectManager.init(this) } catch (_: Throwable) {}
        try { com.example.gemini.data.updater.PluginUpdateCheckWorker.schedule(this) } catch (_: Throwable) {}

        // Link SAIF AI AUTOMATION to the API system of the Settings panel: whatever the user saved
        // there (provider, key, model) is what the automation engine uses.
        SaifAutomation.link(
            apiResolver = {
                val state = ProviderSettingsManager.loadState()
                val key = state.apiKeys.getOrNull(state.currentKeyIndex)?.takeIf { it.isNotBlank() }
                    ?: state.apiKeys.firstOrNull { it.isNotBlank() }
                    ?: ""
                SaifApiConfig(
                    provider = state.providerName,
                    apiKey = key,
                    model = state.activeModel
                )
            },
            openHostSettings = { HostNavigation.openSettingsRequest.value = true }
        )

        // The live engine speaks with the voice the user picked in SAIF AI Settings (SAIF ADVANCE VOICE by default),
        // not with a voice chosen inside the automation module.
        SaifAutomation.hostLiveTurnFactory = { context, prompt, listener ->
            com.example.live.LiveTurnLoop(context, prompt, listener)
        }
        SaifAutomation.hostCanRespondWithoutKey = { com.example.live.LiveCodingBridge.available() }
        SaifAutomation.hostVoiceResolver = { com.example.utils.SaifVoice.liveVoiceName(this) }
        SaifAutomation.hostMemoryResolver = { feature -> com.example.util.SaifMemoryCore.promptBlock(feature) }
        SaifAutomation.hostAutoLanguageResolver = { com.example.util.SaifMemoryCore.autoLanguage.value }
    }
}
