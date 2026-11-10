/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import com.example.data.local.AuthUser
import com.example.gemini.data.remote.AgyHubClient.AgyAuthInfo
import com.example.gemini.data.remote.AgyHubClient.AgyAuthStatus
import com.example.gemini.data.remote.GoogleAccountBinding
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleAccountBindingTest {
    private val localUser = AuthUser(email = "local@example.com", name = "Local", uid = "local:123", isFirebaseUser = false)
    private val hub = AgyAuthInfo(status = AgyAuthStatus.AUTHENTICATED, isLoggedIn = true, email = "other@gmail.com")
    private fun reason(user: AuthUser?, info: AgyAuthInfo, bound: String? = user?.uid, identity: String? = info.email) =
        GoogleAccountBinding.denialReason(user, info, bound, identity)

    @Test fun localAndGuestAppAccountsCanUseTheirOwnAntigravitySignIn() {
        assertNull(reason(localUser, hub))
        assertNull(reason(localUser.copy(email = "guest@saif.ai", uid = "guest:123"), hub.copy(email = "")))
        assertNull(reason(localUser.copy(isFirebaseUser = true, uid = "firebase-1"), hub))
    }

    @Test fun aPreviousAppAccountsHubTokenCannotBeAdopted() {
        assertNotNull(reason(localUser, hub, bound = "local:previous"))
        assertNotNull(reason(localUser, hub, bound = null))
        assertNotNull(reason(localUser, hub.copy(email = "switched@gmail.com"), identity = "other@gmail.com"))
    }

    @Test fun unconnectedHubCannotAct() {
        assertNotNull(reason(localUser, hub.copy(isOffline = true)))
        assertNotNull(reason(localUser, hub.copy(isLoggedIn = false)))
        assertNotNull(reason(localUser, hub.copy(status = AgyAuthStatus.CHECKING)))
    }

    @Test fun loggedOutOrSuspendedAppAccountCannotAct() {
        assertNotNull(reason(null, hub))
        assertNotNull(reason(localUser.copy(uid = ""), hub))
        assertNotNull(reason(localUser.copy(isBanned = true), hub))
    }
}
