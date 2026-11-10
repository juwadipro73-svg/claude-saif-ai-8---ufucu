/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.data.remote

import android.content.Context
import android.content.SharedPreferences

/** A hub token on this device is usable only by the SAIF AI account that explicitly connected it. */
object AntigravityAppBinding {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("saif_antigravity_binding", Context.MODE_PRIVATE)
    }

    val ownerUid: String? get() = prefs?.getString("owner_uid", null)
    val hubIdentity: String? get() = prefs?.getString("hub_identity", null)

    fun record(owner: String, hub: AgyHubClient.AgyAuthInfo) {
        require(owner.isNotBlank() && hub.isLoggedIn && hub.status == AgyHubClient.AgyAuthStatus.AUTHENTICATED)
        val identity = hub.email.ifBlank { hub.username }.trim().lowercase()
        prefs?.edit()?.putString("owner_uid", owner)?.putString("hub_identity", identity)?.apply()
    }

    fun clear() { prefs?.edit()?.remove("owner_uid")?.remove("hub_identity")?.apply() }
}
