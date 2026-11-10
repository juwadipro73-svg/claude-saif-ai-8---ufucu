/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import com.example.data.local.LocalIdentity
import com.example.data.local.PasswordHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class LocalIdentityPasswordTest {
    @Test fun ownerIsStableCaseInsensitiveAndNotFirebaseUid() {
        assertEquals(LocalIdentity.uidForEmail(" Alice@Example.com "), LocalIdentity.uidForEmail("alice@example.com"))
        assertTrue(LocalIdentity.uidForEmail("alice@example.com").startsWith("local:"))
        assertNotEquals(LocalIdentity.uidForEmail("alice@example.com"), LocalIdentity.uidForEmail("bob@example.com"))
    }

    @Test fun pbkdf2UsesRandomSaltAndRejectsWrongPassword() {
        val first = PasswordHash.create("correct horse battery staple")
        val second = PasswordHash.create("correct horse battery staple")
        assertNotEquals(first, second)
        assertTrue(PasswordHash.verify("correct horse battery staple", first))
        assertFalse(PasswordHash.verify("wrong", first))
        assertFalse(PasswordHash.verify("anything", "pbkdf2:999999999:bad:bad"))
    }

    @Test fun legacySha256RemainsReadableForMigration() {
        val old = MessageDigest.getInstance("SHA-256").digest("old password".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(PasswordHash.verify("old password", old))
        assertTrue(PasswordHash.needsUpgrade(old))
        assertFalse(PasswordHash.verify("not old", old))
        assertFalse(PasswordHash.needsUpgrade(PasswordHash.create("new password")))
    }
}
