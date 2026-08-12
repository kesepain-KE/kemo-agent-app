package com.kesepain.kemoapp.ui.screens.chat

import com.kesepain.kemoapp.data.remote.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTokenSnapshotTest {
    @Test
    fun newSnapshotOverridesStaleCompatibilityWindow() {
        val value = ApiClient.json.parseToJsonElement(
            """{
              "overview": {
                "context_snapshot": {
                  "available": true,
                  "system_prompt_tokens": 100,
                  "tool_schema_tokens": 20,
                  "conversation_tokens": 300,
                  "summary_tokens": 40,
                  "other_tokens": 5,
                  "total_tokens": 465,
                  "capacity_tokens": 1000,
                  "percent": 46.5,
                  "source": "runtime_recalculated",
                  "measurement": "estimated"
                },
                "context_window": {"tokens": {"context_tokens": 72000, "percent": 0}}
              }
            }""",
        )

        val snapshot = contextTokenSnapshot(value)
        assertTrue(snapshot.available)
        assertEquals(465L, snapshot.totalTokens)
        assertEquals(340L, snapshot.conversationTokens + snapshot.summaryTokens)
        assertEquals(46.5, snapshot.percent, 0.001)
    }

    @Test
    fun legacyWindowPrefersTotalTokensAndDerivesPercent() {
        val value = ApiClient.json.parseToJsonElement(
            """{"overview":{"context_window":{"tokens":{
              "system_prompt_tokens":100,"context_tokens":900,"total_tokens":1000,"capacity_tokens":2000
            }}}}""",
        )
        val snapshot = contextTokenSnapshot(value)
        assertEquals(1000L, snapshot.totalTokens)
        assertEquals(50.0, snapshot.percent, 0.001)
    }

    @Test
    fun authoritativeUnavailableSnapshotDoesNotReuseStaleWindowAvailability() {
        val value = ApiClient.json.parseToJsonElement(
            """{"overview":{"context_snapshot":{"available":false,"source":"unavailable"},
              "context_window":{"tokens":{"total_tokens":9000}}}}""",
        )
        assertFalse(contextTokenSnapshot(value).available)
    }
}
