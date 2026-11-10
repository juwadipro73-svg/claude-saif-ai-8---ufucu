/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.data.local

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2 for new local credentials; read-and-upgrade compatibility with the original SHA-256 rows. */
object PasswordHash {
    private const val ROUNDS = 160_000
    private const val BYTES = 32
    private val legacy = Regex("[a-fA-F0-9]{64}")

    fun create(password: String): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val key = derive(password, salt, ROUNDS)
        return "pbkdf2:$ROUNDS:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(key)}"
    }

    fun verify(password: String, encoded: String): Boolean = runCatching {
        if (legacy.matches(encoded)) {
            val actual = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val expected = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            MessageDigest.isEqual(actual, expected)
        } else {
            val parts = encoded.split(':')
            if (parts.size != 4 || parts[0] != "pbkdf2") return@runCatching false
            val rounds = parts[1].toIntOrNull() ?: return@runCatching false
            if (rounds !in 100_000..500_000) return@runCatching false
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            if (salt.size != 16 || expected.size != BYTES) return@runCatching false
            MessageDigest.isEqual(derive(password, salt, rounds), expected)
        }
    }.getOrDefault(false)

    fun needsUpgrade(encoded: String): Boolean = legacy.matches(encoded)

    private fun derive(password: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, BYTES * 8)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
