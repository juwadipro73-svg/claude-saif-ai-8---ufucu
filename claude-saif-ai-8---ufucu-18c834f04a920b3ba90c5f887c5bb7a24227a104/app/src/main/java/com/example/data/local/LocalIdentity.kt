/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.data.local

import java.security.MessageDigest

/** Stable, device-local owner key. Not a Firebase identity or proof of an email address. */
object LocalIdentity {
    fun uidForEmail(email: String): String {
        val normalized = email.trim().lowercase()
        require(normalized.isNotBlank()) { "Email is required" }
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return "local:" + digest.joinToString("") { "%02x".format(it) }
    }

    fun isGuest(email: String): Boolean = email.equals("guest@saif.ai", ignoreCase = true)
}
