/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.utils

import android.content.Context
import com.example.ui.components.VOICE_PROFILES
import com.example.ui.components.VoiceProfile

/**
 * ONE voice for the whole app, and it is now PERMANENT.
 *
 * Every speaking feature (chat replies, live baatcheet, video narration, previews, the automation
 * engine) asks here first. The "Voice & TTS" section was removed from SAIF AI's Settings panel in
 * this build, so the choice can no longer be changed from the UI: **Kabir** is the app's fixed
 * voice. Any value an older build left behind is rewritten to Kabir the first time it is read, so
 * the TTS engine, the live engine and the video director can never disagree again.
 */
object SaifVoice {

    /** Kabir is the app's permanent voice. The voice picker UI no longer exists. */
    const val LOCKED_VOICE_ID = "Kabir"

    /** Id of the SAIF AI Automation voice, kept so old saved values are still understood. */
    const val SAIF_ADVANCE = "SaifAdvance"

    /** Fallback when nothing was ever chosen. */
    const val DEFAULT = LOCKED_VOICE_ID

    private const val VOICE_PREFS = "VoiceSettings"
    private const val BRAND_PREFS = "saif_brand_kit"
    private const val KEY_SELECTED = "selected_voice_id"

    /** The voice name a Gemini-Live style provider is asked for, per app voice. */
    private val LIVE_VOICE_NAMES = mapOf(
        SAIF_ADVANCE to "Puck",      // the SAIF AI Automation voice
        "Kabir" to "Charon",         // deep, authoritative
        "Priya" to "Aoede"           // bright, warm
    )

    /**
     * Id of the app's voice. Always [LOCKED_VOICE_ID]: the setting is hidden, so nothing else can be
     * chosen. An install upgraded from an older build gets its stale value rewritten here.
     */
    fun selectedId(context: Context): String {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SELECTED, null)
        val brand = app.getSharedPreferences(BRAND_PREFS, Context.MODE_PRIVATE).getString("voice", null)
        if (saved != LOCKED_VOICE_ID || brand != LOCKED_VOICE_ID) {
            prefs.edit().putString(KEY_SELECTED, LOCKED_VOICE_ID).apply()
            app.getSharedPreferences(BRAND_PREFS, Context.MODE_PRIVATE)
                .edit().putString("voice", LOCKED_VOICE_ID).apply()
        }
        return LOCKED_VOICE_ID
    }

    /** The full profile of the app's voice. */
    fun selected(context: Context): VoiceProfile =
        VOICE_PROFILES.firstOrNull { it.id == selectedId(context) } ?: VOICE_PROFILES.first()

    /**
     * Kept so older call sites (the video director's brand kit, for example) still compile. The voice
     * is permanent now, so this always stores Kabir regardless of the id it is handed.
     */
    fun select(context: Context, id: String) {
        val app = context.applicationContext
        app.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED, LOCKED_VOICE_ID).apply()
        app.getSharedPreferences(BRAND_PREFS, Context.MODE_PRIVATE)
            .edit().putString("voice", LOCKED_VOICE_ID).apply()
    }

    /** Writes Kabir into every stored voice slot. Safe to call at startup. */
    fun enforceLock(context: Context) {
        selectedId(context.applicationContext)
    }

    /** The voice the video director narrates with. It is the same permanent voice as the chat. */
    fun narrationVoiceId(context: Context): String = selectedId(context)

    fun narrationProfile(context: Context): VoiceProfile =
        VOICE_PROFILES.firstOrNull { it.id == narrationVoiceId(context) } ?: VOICE_PROFILES.first()

    /** Voice name to request from a Gemini-Live style provider: Kabir's. */
    fun liveVoiceName(context: Context): String = LIVE_VOICE_NAMES[selectedId(context)] ?: "Charon"

    /** Every voice name the live engine may ask for, so a provider that does not know one can try the next. */
    fun liveVoiceFallbacks(context: Context): List<String> {
        val first = liveVoiceName(context)
        return (listOf(first) + LIVE_VOICE_NAMES.values).distinct()
    }

    /** Human name shown in reports and toasts. */
    fun displayName(context: Context): String = selected(context).name
}
