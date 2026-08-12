package com.kesepain.kemoapp.ui.screens.config

import com.kesepain.kemoapp.data.remote.ApiClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningEffortOptionsTest {
    @Test
    fun chatUsesFixedFiveEfforts() {
        val result = reasoningEffortOptions("chat", "gpt", "high", null)
        assertEquals(listOf("minimal", "low", "medium", "high", "max"), result.options)
        assertEquals("high", result.selected)
        assertTrue(result.available)
    }

    @Test
    fun kemoUsesDeclaredOrderFiltersNoneAndFallsBackToMedium() {
        val capabilities = ApiClient.json.parseToJsonElement(
            """{"model":"m1","capabilities":{"reasoning":{"supported":true,
              "efforts":["low","none","medium","xhigh","low"]}}}""",
        )
        val result = reasoningEffortOptions("kemo", "m1", "max", capabilities)
        assertEquals(listOf("low", "medium", "xhigh"), result.options)
        assertEquals("medium", result.selected)
    }

    @Test
    fun kemoDoesNotGuessOptionsWhenCapabilityIsMissing() {
        val result = reasoningEffortOptions("kemo", "m1", "medium", null)
        assertFalse(result.available)
        assertEquals(emptyList<String>(), result.options)
        assertEquals("medium", result.selected)
    }

    @Test
    fun configRoundTripPersistsReasoningEffort() {
        val changes = UserConfigDraft(reasoningEffort = "high").toChanges()
        val provider = changes["provider"] as JsonObject
        assertEquals("high", (provider["reasoning_effort"] as JsonPrimitive).content)
    }
}
