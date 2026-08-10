package com.kesepain.kemoapp.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamParserTest {
    private val parser = ChatStreamParser()

    @Test
    fun `usage event exposes tokens and computed cache hit rate`() {
        val event = parser.parse(
            """data: {"type":"usage","usage":{"prompt_tokens":100,"completion_tokens":50,"cached_prompt_tokens":40}}""",
        ) as StreamEvent.Usage

        assertEquals(150L, event.value.totalTokens)
        assertEquals(0.4, event.value.cacheHitRate, 0.0001)
        assertEquals(0L, event.value.elapsedMs)
    }

    @Test
    fun `done event carries declared usage and elapsed time`() {
        val event = parser.parse(
            """data: {"type":"done","usage":{"total_tokens":75,"cache_hit_rate":0.25},"metadata":{"elapsed_ms":1234}}""",
        ) as StreamEvent.Done

        val usage = event.value
        assertTrue(usage != null)
        assertEquals(75L, usage?.totalTokens)
        assertEquals(0.25, usage?.cacheHitRate ?: 0.0, 0.0001)
        assertEquals(1234L, usage?.elapsedMs)
    }
}
