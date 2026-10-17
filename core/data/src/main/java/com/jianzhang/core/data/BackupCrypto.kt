package com.jianzhang.core.data

import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * v3 backup envelope.
 *
 * Layout:
 * {
 *   "format": "jianzhang-backup",
 *   "version": 3,
 *   "encrypted": true,
 *   "kdf": "PBKDF2-HMAC-SHA256",
 *   "iterations": 210000,
 *   "salt": "<base64 16 bytes>",
 *   "cipher": "AES-256-GCM",
 *   "iv": "<base64 12 bytes>",
 *   "ciphertext": "<base64 ciphertext + 16-byte GCM tag>",
 *   "exportedAt": "<ISO-8601>"
 * }
 *
 * The v2 plaintext JSON is encrypted as a whole. The envelope header is bound
 * as GCM additional authenticated data, so changing the KDF parameters or
 * cipher identity is detected before any ledger data is touched.
 */
object BackupCrypto {
    const val VERSION = 3
    const val MIN_PASSWORD_LENGTH = 8

    private const val FORMAT = "jianzhang-backup"
    private const val KDF = "PBKDF2-HMAC-SHA256"
    private const val CIPHER = "AES-256-GCM"
    private const val DEFAULT_ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MAX_PASSWORD_LENGTH = 1_024
    private const val MAX_CIPHERTEXT_BYTES = 64 * 1024 * 1024

    fun validateExportPassword(password: String): String? = when {
        password.length < MIN_PASSWORD_LENGTH -> "备份口令至少需要 $MIN_PASSWORD_LENGTH 位"
        password.length > MAX_PASSWORD_LENGTH -> "备份口令过长"
        else -> null
    }

    fun encrypt(plaintext: String, password: String): String {
        validateExportPassword(password)?.let { throw IllegalArgumentException(it) }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val keyBytes = deriveKey(password, salt, DEFAULT_ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            cipher.updateAAD(additionalAuthenticatedData(DEFAULT_ITERATIONS))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            JSONObject().apply {
                put("format", FORMAT)
                put("version", VERSION)
                put("encrypted", true)
                put("kdf", KDF)
                put("iterations", DEFAULT_ITERATIONS)
                put("salt", base64(salt))
                put("cipher", CIPHER)
                put("iv", base64(iv))
                put("ciphertext", base64(ciphertext))
                put("exportedAt", Instant.now().toString())
            }.toString(2)
        } finally {
            keyBytes.fill(0)
        }
    }

    fun decrypt(raw: String, password: String): String {
        val envelope = runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalArgumentException("无法解析备份文件")
        }
        if (!isEncrypted(envelope)) {
            throw IllegalArgumentException("备份文件不是 v3 加密格式")
        }
        if (password.isEmpty() || password.length > MAX_PASSWORD_LENGTH) {
            throw IllegalArgumentException("请输入有效的备份口令")
        }
        val iterations = envelope.optInt("iterations", -1)
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw IllegalArgumentException("备份文件的加密参数无效")
        }
        if (envelope.optString("kdf") != KDF || envelope.optString("cipher") != CIPHER) {
            throw IllegalArgumentException("不支持的备份加密算法")
        }
        val salt = decodeBase64(envelope, "salt")
        val iv = decodeBase64(envelope, "iv")
        val ciphertext = decodeBase64(envelope, "ciphertext")
        if (salt.size != SALT_BYTES) throw IllegalArgumentException("备份文件的 salt 无效")
        if (iv.size != IV_BYTES) throw IllegalArgumentException("备份文件的 iv 无效")
        if (ciphertext.size <= TAG_BITS / 8) throw IllegalArgumentException("备份文件内容不完整")
        if (ciphertext.size > MAX_CIPHERTEXT_BYTES) throw IllegalArgumentException("备份文件过大")

        val keyBytes = deriveKey(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            cipher.updateAAD(additionalAuthenticatedData(iterations))
            val plaintext = cipher.doFinal(ciphertext)
            plaintext.toString(Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("备份口令错误，或文件已被篡改")
        } catch (error: GeneralSecurityException) {
            throw IllegalArgumentException("备份解密失败", error)
        } finally {
            keyBytes.fill(0)
        }
    }

    fun isEncrypted(raw: String): Boolean {
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        return isEncrypted(envelope)
    }

    fun isEncrypted(envelope: JSONObject): Boolean {
        if (envelope.optString("format") != FORMAT) return false
        if (envelope.optInt("version", 0) != VERSION) return false
        return envelope.optBoolean("encrypted", false)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } catch (error: GeneralSecurityException) {
            throw IllegalArgumentException("无法派生备份密钥", error)
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun additionalAuthenticatedData(iterations: Int): ByteArray =
        "$FORMAT|v$VERSION|$KDF|$iterations|$CIPHER".toByteArray(Charsets.UTF_8)

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decodeBase64(envelope: JSONObject, key: String): ByteArray {
        val value = envelope.optString(key)
        if (value.isBlank()) throw IllegalArgumentException("备份文件缺少 $key")
        return runCatching { Base64.getDecoder().decode(value) }.getOrElse {
            throw IllegalArgumentException("备份文件的 $key 编码无效")
        }
    }
}

class BackupPasswordRequiredException :
    IllegalArgumentException("该备份已加密，需要输入备份口令")

