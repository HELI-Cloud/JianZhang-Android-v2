package com.jianzhang.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PasswordHasherTest {
    @Test
    fun `pbkdf2 hash verifies and rejects a wrong password`() {
        val salt = PasswordHasher.newSalt()
        val stored = PasswordHasher.pbkdf2Hash("correct horse battery staple", salt)

        assertThat(PasswordHasher.verifyStored("correct horse battery staple", "", stored))
            .isEqualTo(PasswordHasher.StoredHashFormat.PBKDF2)
        assertThat(PasswordHasher.verifyStored("wrong password", "", stored)).isNull()
    }

    @Test
    fun `legacy sha256 hash still verifies`() {
        val salt = "legacy-salt"
        val stored = "sha256${'$'}$salt${'$'}${PasswordHasher.legacyHash("123456", salt)}"

        assertThat(PasswordHasher.verifyStored("123456", "", stored))
            .isEqualTo(PasswordHasher.StoredHashFormat.LEGACY_SHA256)
        assertThat(PasswordHasher.verifyStored("123457", "", stored)).isNull()
    }

    @Test
    fun `older pbkdf2 counts still verify and are marked for rehash`() {
        val salt = PasswordHasher.newSalt()
        val spec = javax.crypto.spec.PBEKeySpec(
            "old password".toCharArray(),
            android.util.Base64.decode(salt, android.util.Base64.NO_WRAP),
            120_000,
            256,
        )
        val key = javax.crypto.SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        spec.clearPassword()
        val stored = "pbkdf2" + '$' + "120000" + '$' + salt + '$' +
            android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)

        assertThat(PasswordHasher.verifyStored("old password", "", stored))
            .isEqualTo(PasswordHasher.StoredHashFormat.PBKDF2_OUTDATED)
        assertThat(PasswordHasher.verifyStored("wrong", "", stored)).isNull()
    }

    @Test
    fun `invalid iteration counts never authenticate`() {
        val salt = PasswordHasher.newSalt()
        val expected = PasswordHasher.hash("pw", salt)
        assertThat(PasswordHasher.verifyStored("pw", "", "pbkdf2" + '$' + "0" + '$' + salt + '$' + expected)).isNull()
        assertThat(PasswordHasher.verifyStored("pw", "", "pbkdf2" + '$' + "99999999" + '$' + salt + '$' + expected)).isNull()
    }

    @Test
    fun `blank stored values never authenticate`() {
        assertThat(PasswordHasher.verifyStored("", "", "")).isNull()
        assertThat(PasswordHasher.verify("x", "", "x")).isFalse()
        assertThat(PasswordHasher.verifyLegacy("x", "", "x")).isFalse()
    }
}


