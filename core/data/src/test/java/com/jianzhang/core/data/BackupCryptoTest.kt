package com.jianzhang.core.data

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupCryptoTest {
    private val password = "correct horse battery staple"
    private val plaintext = """
        {"format":"jianzhang-backup","version":2,"transactions":[{"note":"午饭","amount_cents":12345}]}
    """.trimIndent()

    @Test
    fun `v3 encrypts and decrypts the complete ledger payload`() {
        val envelope = BackupCrypto.encrypt(plaintext, password)

        assertThat(BackupCrypto.isEncrypted(envelope)).isTrue()
        assertThat(envelope).contains("\"version\": 3")
        assertThat(envelope).doesNotContain("午饭")
        assertThat(envelope).doesNotContain("12345")
        assertThat(BackupCrypto.decrypt(envelope, password)).isEqualTo(plaintext)
    }

    @Test
    fun `each export uses an independent salt and iv`() {
        val first = JSONObject(BackupCrypto.encrypt(plaintext, password))
        val second = JSONObject(BackupCrypto.encrypt(plaintext, password))

        assertThat(first.getString("salt")).isNotEqualTo(second.getString("salt"))
        assertThat(first.getString("iv")).isNotEqualTo(second.getString("iv"))
        assertThat(first.getString("ciphertext")).isNotEqualTo(second.getString("ciphertext"))
    }

    @Test
    fun `wrong password is rejected without returning plaintext`() {
        val envelope = BackupCrypto.encrypt(plaintext, password)

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(envelope, "wrong password")
        }
        assertThat(error).hasMessageThat().contains("口令错误")
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val json = JSONObject(BackupCrypto.encrypt(plaintext, password))
        val ciphertext = Base64.getDecoder().decode(json.getString("ciphertext"))
        ciphertext[ciphertext.lastIndex] = (ciphertext.last() + 1).toByte()
        json.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))

        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(json.toString(), password)
        }
    }

    @Test
    fun `tampered kdf parameters are rejected`() {
        val json = JSONObject(BackupCrypto.encrypt(plaintext, password))
        json.put("iterations", 100_001)

        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(json.toString(), password)
        }
    }

    @Test
    fun `short export passwords are rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt(plaintext, "1234567")
        }
        assertThat(error).hasMessageThat().contains("至少")
    }

    @Test
    fun `missing envelope fields are rejected`() {
        val json = JSONObject(BackupCrypto.encrypt(plaintext, password))
        json.remove("salt")

        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(json.toString(), password)
        }
    }
}

