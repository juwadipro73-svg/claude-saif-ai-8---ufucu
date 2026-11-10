/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.data.remote

import com.example.data.local.AuthUser

/**
 * Antigravity has its own OAuth session. The restored SAIF AI startup login (Firebase
 * email/password, local account, or guest) is not a Google OAuth identity and must not
 * be compared to the Antigravity email or used as an Antigravity authorization token.
 * Kept under the old name so existing chat/voice/engine gates share one policy.
 */
object GoogleAccountBinding {
    fun denialReason(
        appUser: AuthUser?, hub: AgyHubClient.AgyAuthInfo,
        boundOwnerUid: String? = AntigravityAppBinding.ownerUid,
        boundHubIdentity: String? = AntigravityAppBinding.hubIdentity
    ): String? {
        if (appUser == null || appUser.uid.isBlank() || !appUser.isActive || appUser.isBanned) {
            return "Sign in to SAIF AI first."
        }
        if (!hub.isLoggedIn || hub.status != AgyHubClient.AgyAuthStatus.AUTHENTICATED || hub.isOffline) {
            return "Antigravity is not connected. Open its sign-in screen inside SAIF AI."
        }
        if (boundOwnerUid != appUser.uid) {
            return "This Antigravity session belongs to another app account. Sign in again inside SAIF AI."
        }
        val currentHub = hub.email.ifBlank { hub.username }.trim().lowercase()
        if (!boundHubIdentity.isNullOrBlank() && boundHubIdentity != currentHub) {
            return "Antigravity account changed. Reconnect it inside SAIF AI."
        }
        return null
    }
}
