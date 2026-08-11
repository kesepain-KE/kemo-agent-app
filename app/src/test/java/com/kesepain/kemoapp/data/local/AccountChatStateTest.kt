package com.kesepain.kemoapp.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountChatStateTest {
    @Test
    fun accountStatesRemainIsolated() {
        val states = mapOf(
            "account-a" to AccountChatState("[\"a\"]", "app-a"),
            "account-b" to AccountChatState("[\"b\"]", "app-b"),
        )

        assertEquals("app-a", resolveAccountChatState("account-a", states, "", "[]", "").sessionId)
        assertEquals("app-b", resolveAccountChatState("account-b", states, "", "[]", "").sessionId)
    }

    @Test
    fun legacyStateBelongsOnlyToItsRecordedAccount() {
        val ownerState = resolveAccountChatState(
            accountId = "account-a",
            states = emptyMap(),
            legacyOwner = "account-a",
            legacyHistory = "[\"legacy\"]",
            legacySessionId = "app-legacy",
        )
        val otherState = resolveAccountChatState(
            accountId = "account-b",
            states = emptyMap(),
            legacyOwner = "account-a",
            legacyHistory = "[\"legacy\"]",
            legacySessionId = "app-legacy",
        )

        assertEquals("app-legacy", ownerState.sessionId)
        assertEquals(AccountChatState(), otherState)
    }
}
