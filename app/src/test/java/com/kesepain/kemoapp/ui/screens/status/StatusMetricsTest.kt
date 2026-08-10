package com.kesepain.kemoapp.ui.screens.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StatusMetricsTest {
    @Test
    fun `summary row is removed and duplicate metric categories collapse`() {
        val visible = statusMetricsForDisplay(
            listOf(
                "health" to "ok",
                "version" to "1.0",
                "runtime · context_total" to "100000",
                "runtime · context_used" to "25000",
                "runtime · cache_hits" to "20",
                "runtime · cache_rate" to "50%",
                "tools · image_generation" to "enabled",
                "tools · image_edit" to "enabled",
                "sessions" to "3",
            ),
        )

        assertFalse(visible.any { metricCategory(it.first) == "health" })
        assertEquals(1, visible.count { metricCategory(it.first) == "context" })
        assertEquals(1, visible.count { metricCategory(it.first) == "cache" })
        assertEquals(1, visible.count { metricCategory(it.first) == "image" })
        assertEquals(1, visible.count { metricCategory(it.first) == "session" })
    }
}
