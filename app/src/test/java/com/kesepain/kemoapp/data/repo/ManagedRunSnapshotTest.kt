package com.kesepain.kemoapp.data.repo

import com.kesepain.kemoapp.data.remote.ApiClient
import com.kesepain.kemoapp.data.stream.StreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedRunSnapshotTest {
    @Test
    fun parsesReplayCursorAndVisibleEvents() {
        val value = ApiClient.json.parseToJsonElement(
            """{
              "run_id":"run-1",
              "session_id":"app-1",
              "status":"running",
              "terminal":false,
              "recoverable":true,
              "created_at":123,
              "last_event_id":3,
              "events":[
                {"event_id":1,"data":"{\"type\":\"text_delta\",\"text\":\"你\"}"},
                {"event_id":2,"data":"{\"type\":\"long_task_update\"}"},
                {"event_id":3,"data":"{\"type\":\"text_delta\",\"text\":\"好\"}"}
              ]
            }""",
        )

        val snapshot = parseManagedRunSnapshot(value)

        assertEquals("run-1", snapshot.runId)
        assertEquals("app-1", snapshot.sessionId)
        assertEquals(123, snapshot.createdAt)
        assertEquals(3, snapshot.lastEventId)
        assertFalse(snapshot.terminal)
        assertTrue(snapshot.recoverable)
        assertEquals(2, snapshot.events.size)
        assertEquals("你", (snapshot.events[0].event as StreamEvent.Text).text)
        assertEquals("好", (snapshot.events[1].event as StreamEvent.Text).text)
    }
}
