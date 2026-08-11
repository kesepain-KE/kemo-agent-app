package com.kesepain.kemoapp.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountTransferCodecTest {
    private val payload = AccountTransferPayload(
        displayName = "Desk agent",
        baseUrl = "https://kemo.example:8742",
        username = "kesepain",
        deviceToken = "device-secret-token",
        userPassword = "user-secret-password",
        sessionToken = "session-secret-token",
        sessionExpiresAt = 1_900_000_000L,
        exportedAt = 1_786_435_200L,
    )

    @Test
    fun encryptedAccountRoundTripsWithoutPlaintextMetadata() {
        val password = "portable-password".toCharArray()
        val encrypted = AccountTransferCodec.encrypt(payload, password)

        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains(payload.username))
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains(payload.deviceToken))
        assertEquals(payload, AccountTransferCodec.decrypt(encrypted, password))
    }

    @Test
    fun wrongPasswordCannotDecryptAccountFile() {
        val encrypted = AccountTransferCodec.encrypt(payload, "correct-password".toCharArray())

        assertThrows(Exception::class.java) {
            AccountTransferCodec.decrypt(encrypted, "wrong-password".toCharArray())
        }
    }

    @Test
    fun authenticatedEncryptionRejectsTampering() {
        val encrypted = AccountTransferCodec.encrypt(payload, "portable-password".toCharArray())
        val tampered = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertThrows(Exception::class.java) {
            AccountTransferCodec.decrypt(tampered, "portable-password".toCharArray())
        }
        assertArrayEquals(encrypted.copyOfRange(0, 8), tampered.copyOfRange(0, 8))
    }
}
