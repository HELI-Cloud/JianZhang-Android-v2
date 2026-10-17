package com.jianzhang.core.common

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    /** Current KDF cost. Stored hashes carry their own count so this can be raised later. */
    private const val ITERATIONS = 310_000
    private const val KEY_LENGTH = 256
    private const val MIN_ACCEPTED_ITERATIONS = 10_000
    private const val MAX_ACCEPTED_ITERATIONS = 1_000_000

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String = hash(password, salt, ITERATIONS)

    private fun hash(password: String, salt: String, iterations: Int): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), iterations, KEY_LENGTH)
        return try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            Base64.encodeToString(key, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(password: String, salt: String, expected: String): Boolean =
        verify(password, salt, expected, ITERATIONS)

    private fun verify(password: String, salt: String, expected: String, iterations: Int): Boolean {
        if (salt.isBlank() || expected.isBlank()) return false
        return runCatching {
            val actual = hash(password, salt, iterations)
            actual.length == expected.length && constantTimeEquals(actual, expected)
        }.getOrDefault(false)
    }

    /** Current on-disk format: pbkdf2$<iterations>$<salt>$<base64-hash>. */
    fun pbkdf2Hash(password: String, salt: String): String = "pbkdf2\$$ITERATIONS\$$salt\$${hash(password, salt)}"

    /** Legacy desktop/0.6 format: sha256$<salt>$<lowercase-hex(sha256(salt + password))>. */
    fun legacyHash(password: String, salt: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest((salt + password).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    fun verifyLegacy(password: String, salt: String, expectedHex: String): Boolean {
        if (salt.isBlank() || expectedHex.isBlank()) return false
        val actual = legacyHash(password, salt)
        return actual.length == expectedHex.length && constantTimeEquals(actual.lowercase(), expectedHex.lowercase())
    }

    /**
     * Verifies any supported stored representation.
     * Returns the format that matched so callers can migrate legacy or outdated hashes.
     */
    fun verifyStored(password: String, fallbackSalt: String, stored: String): StoredHashFormat? {
        if (stored.isBlank()) return null
        val parts = stored.split('$')
        return when (parts.firstOrNull()) {
            "sha256" -> {
                val salt = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: fallbackSalt
                val expected = parts.getOrNull(2).orEmpty()
                if (verifyLegacy(password, salt, expected)) StoredHashFormat.LEGACY_SHA256 else null
            }
            "pbkdf2" -> {
                val iterations = parts.getOrNull(1)?.toIntOrNull()
                    ?.takeIf { it in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS }
                    ?: return null
                val salt = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: fallbackSalt
                val expected = parts.getOrNull(3).orEmpty()
                if (!verify(password, salt, expected, iterations)) return null
                if (iterations != ITERATIONS) StoredHashFormat.PBKDF2_OUTDATED else StoredHashFormat.PBKDF2
            }
            else -> null
        }
    }

    enum class StoredHashFormat { PBKDF2, PBKDF2_OUTDATED, LEGACY_SHA256 }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
