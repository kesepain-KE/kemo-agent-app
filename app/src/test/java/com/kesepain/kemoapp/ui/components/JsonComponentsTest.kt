package com.kesepain.kemoapp.ui.components

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonComponentsTest {
    @Test
    fun emptyFileItemsDoNotBecomeEnvelopeRecord() {
        val payload = Json.parseToJsonElement(
            """{"path":"","items":[],"pagination":{"page":1,"total_pages":1}}""",
        )

        assertTrue(payload.records("items", "files", "entries").isEmpty())
    }

    @Test
    fun emptySenseSourcesDoNotBecomeEnvelopeRecord() {
        val payload = Json.parseToJsonElement(
            """{"sources":[],"count":0,"updated_at":"2026-08-11T20:01:31Z"}""",
        )

        assertTrue(payload.records("sources").isEmpty())
    }
}
