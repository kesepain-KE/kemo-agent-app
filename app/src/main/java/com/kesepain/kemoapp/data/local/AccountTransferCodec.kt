package com.kesepain.kemoapp.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class AccountTransferPayload(
    val formatVersion: Int = AccountTransferCodec.FORMAT_VERSION,
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val deviceToken: String = "",
    val userPassword: String = "",
    val sessionToken: String = "",
    val sessionExpiresAt: Long = 0L,
    val exportedAt: Long,
)

/**
 * Portable encrypted account file format.
 *
 * The payload is JSON encrypted with a password-derived AES-256-GCM key. The
 * file contains only a short format header, a random salt, a random IV and the
 * authenticated ciphertext; account names and credentials never appear as
 * plaintext metadata.
 */
object AccountTransferCodec {
    const val FORMAT_VERSION = 1
    const val MIME_TYPE = "application/vnd.kemo.account"
    const val FILE_EXTENSION = "kemoaccount"
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_FILE_BYTES = 1024 * 1024

    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val MAGIC = "KEMOACC1".toByteArray(Charsets.US_ASCII)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encrypt(payload: AccountTransferPayload, password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH) { "transfer password is too short" }
        require(payload.formatVersion == FORMAT_VERSION) { "unsupported account format" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val plaintext = json.encodeToString(AccountTransferPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val ciphertext = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        return MAGIC + salt + iv + ciphertext
    }

    fun decrypt(fileBytes: ByteArray, password: CharArray): AccountTransferPayload {
        require(password.isNotEmpty()) { "transfer password is required" }
        require(fileBytes.size in MIN_FILE_BYTES..MAX_FILE_BYTES) { "invalid account file size" }
        val magic = fileBytes.copyOfRange(0, MAGIC.size)
        require(magic.contentEquals(MAGIC)) { "invalid account file header" }
        val saltStart = MAGIC.size
        val ivStart = saltStart + SALT_BYTES
        val cipherStart = ivStart + IV_BYTES
        val salt = fileBytes.copyOfRange(saltStart, ivStart)
        val iv = fileBytes.copyOfRange(ivStart, cipherStart)
        val ciphertext = fileBytes.copyOfRange(cipherStart, fileBytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val plaintext = cipher.doFinal(ciphertext)
        return try {
            json.decodeFromString(AccountTransferPayload.serializer(), plaintext.toString(Charsets.UTF_8)).also {
                require(it.formatVersion == FORMAT_VERSION) { "unsupported account format" }
            }
        } finally {
            plaintext.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val MIN_FILE_BYTES = MAGIC.size + SALT_BYTES + IV_BYTES + 16
}
