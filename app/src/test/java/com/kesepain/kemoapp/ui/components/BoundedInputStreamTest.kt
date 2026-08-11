package com.kesepain.kemoapp.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedInputStreamTest {
    @Test
    fun `bounded read returns complete content below the limit`() {
        val source = "kemo".encodeToByteArray()

        val result = ByteArrayInputStream(source).use { it.readUpTo(32) }

        assertArrayEquals(source, result)
    }

    @Test
    fun `bounded read never returns more bytes than requested`() {
        val source = ByteArray(64) { it.toByte() }

        val result = ByteArrayInputStream(source).use { it.readUpTo(17) }

        assertArrayEquals(source.copyOf(17), result)
    }
}
