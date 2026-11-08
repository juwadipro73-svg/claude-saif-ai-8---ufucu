package com.saif.ai.builder.data

import android.content.Context
import com.saif.ai.builder.AppConfig

/**
 * Runtime settings: GitHub token + optional overrides.
 * (Token sirf is device ke private SharedPreferences me rehta hai.)
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("saif_settings", Context.MODE_PRIVATE)

    var githubToken: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var firebaseDbUrlOverride: String
        get() = prefs.getString(KEY_FIREBASE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_FIREBASE, value.trim()).apply()

    var adGateUrlOverride: String
        get() = prefs.getString(KEY_AD_GATE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AD_GATE, value.trim()).apply()

    /** App me set kiya hua URL, warna gradle.properties wali value. */
    val firebaseDbUrl: String
        get() = firebaseDbUrlOverride.ifBlank { AppConfig.firebaseDbUrlDefault }

    val adGateUrl: String
        get() = adGateUrlOverride.ifBlank { AppConfig.adGateUrlDefault }

    private companion object {
        const val KEY_TOKEN = "github_token"
        const val KEY_FIREBASE = "firebase_db_url"
        const val KEY_AD_GATE = "ad_gate_url"
    }
}
