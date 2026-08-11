package com.kesepain.kemoapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.kemoDataStore by preferencesDataStore("kemo_preferences")

@Serializable
data class AccountConfig(
    val id: String,
    val baseUrl: String,
    val username: String,
    val displayName: String = "",
)

@Serializable
data class AccountChatState(
    val historyJson: String = "[]",
    val sessionId: String = "",
)

internal fun resolveAccountChatState(
    accountId: String,
    states: Map<String, AccountChatState>,
    legacyOwner: String,
    legacyHistory: String,
    legacySessionId: String,
): AccountChatState {
    states[accountId]?.let { return it }
    if (accountId.isNotBlank() && (legacyOwner.isBlank() || legacyOwner == accountId)) {
        return AccountChatState(legacyHistory.ifBlank { "[]" }, legacySessionId)
    }
    return AccountChatState()
}

data class AppPreferences(
    val accounts: List<AccountConfig> = emptyList(),
    val currentAccountId: String = "",
    val themeMode: String = "system",
    val tone: String = "Purple",
    val language: String = "system",
    val notifications: Boolean = true,
    val dynamicColor: Boolean = false,
    val biometricEnabled: Boolean = false,
    val chatHistoryJson: String = "[]",
    val chatSessionId: String = "",
    val autoLockMinutes: Int = 5,
    val widgetPending: Int = 0,
    val widgetLatest: String = "",
    val downloadDirectoryUri: String = "",
    val themeBackgroundUri: String = "",
    val themeBackgroundMime: String = "",
)

class Prefs(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    val flow: Flow<AppPreferences> = context.kemoDataStore.data.map { values ->
        val accounts = runCatching { json.decodeFromString<List<AccountConfig>>(values[ACCOUNTS] ?: "[]") }.getOrDefault(emptyList())
        val currentAccountId = values[CURRENT_ACCOUNT].orEmpty()
        val chatStates = runCatching {
            json.decodeFromString<Map<String, AccountChatState>>(values[ACCOUNT_CHAT_STATES] ?: "{}")
        }.getOrDefault(emptyMap())
        val currentChat = resolveAccountChatState(
            accountId = currentAccountId,
            states = chatStates,
            legacyOwner = values[LEGACY_CHAT_ACCOUNT].orEmpty(),
            legacyHistory = values[CHAT_HISTORY] ?: "[]",
            legacySessionId = values[CHAT_SESSION_ID].orEmpty(),
        )
        AppPreferences(
            accounts = accounts,
            currentAccountId = currentAccountId,
            themeMode = values[THEME_MODE] ?: "system",
            tone = values[TONE] ?: "Purple",
            language = values[LANGUAGE] ?: "system",
            notifications = values[NOTIFICATIONS] ?: true,
            dynamicColor = values[DYNAMIC_COLOR] ?: false,
            biometricEnabled = values[BIOMETRIC_ENABLED] ?: false,
            chatHistoryJson = currentChat.historyJson,
            chatSessionId = currentChat.sessionId,
            autoLockMinutes = values[AUTO_LOCK] ?: 5,
            widgetPending = values[WIDGET_PENDING] ?: 0,
            widgetLatest = values[WIDGET_LATEST].orEmpty(),
            downloadDirectoryUri = values[DOWNLOAD_DIRECTORY_URI].orEmpty(),
            themeBackgroundUri = values[THEME_BACKGROUND_URI].orEmpty(),
            themeBackgroundMime = values[THEME_BACKGROUND_MIME].orEmpty(),
        )
    }

    suspend fun snapshot(): AppPreferences = flow.first()

    suspend fun saveAccount(account: AccountConfig, makeCurrent: Boolean = true) {
        context.kemoDataStore.edit { values ->
            val current = runCatching { json.decodeFromString<List<AccountConfig>>(values[ACCOUNTS] ?: "[]") }.getOrDefault(emptyList())
            val updated = current.filterNot { it.id == account.id } + account
            values[ACCOUNTS] = json.encodeToString(updated)
            if (makeCurrent || values[CURRENT_ACCOUNT].isNullOrBlank()) values[CURRENT_ACCOUNT] = account.id
        }
    }

    suspend fun removeAccount(id: String) {
        context.kemoDataStore.edit { values ->
            val current = runCatching {
                json.decodeFromString<List<AccountConfig>>(values[ACCOUNTS] ?: "[]")
            }.getOrDefault(emptyList())
            val remaining = current.filterNot { it.id == id }
            values[ACCOUNTS] = json.encodeToString(remaining)
            val chatStates = runCatching {
                json.decodeFromString<Map<String, AccountChatState>>(values[ACCOUNT_CHAT_STATES] ?: "{}")
            }.getOrDefault(emptyMap()).toMutableMap()
            chatStates.remove(id)
            values[ACCOUNT_CHAT_STATES] = json.encodeToString(chatStates)
            if (values[LEGACY_CHAT_ACCOUNT] == id) {
                values.remove(LEGACY_CHAT_ACCOUNT)
                values.remove(CHAT_HISTORY)
                values.remove(CHAT_SESSION_ID)
            }
            if (values[CURRENT_ACCOUNT] == id) {
                values[CURRENT_ACCOUNT] = remaining.firstOrNull()?.id.orEmpty()
            }
        }
    }

    suspend fun setCurrentAccount(id: String) {
        context.kemoDataStore.edit { values ->
            val previous = values[CURRENT_ACCOUNT].orEmpty()
            val legacyOwner = values[LEGACY_CHAT_ACCOUNT].orEmpty()
            if (previous.isNotBlank() && legacyOwner.isBlank()) {
                val legacyHistory = values[CHAT_HISTORY] ?: "[]"
                val legacySessionId = values[CHAT_SESSION_ID].orEmpty()
                if (legacyHistory != "[]" || legacySessionId.isNotBlank()) {
                    val states = runCatching {
                        json.decodeFromString<Map<String, AccountChatState>>(values[ACCOUNT_CHAT_STATES] ?: "{}")
                    }.getOrDefault(emptyMap()).toMutableMap()
                    states.putIfAbsent(previous, AccountChatState(legacyHistory, legacySessionId))
                    values[ACCOUNT_CHAT_STATES] = json.encodeToString(states)
                    values[LEGACY_CHAT_ACCOUNT] = previous
                }
            }
            values[CURRENT_ACCOUNT] = id
        }
    }
    suspend fun setThemeMode(value: String) = set(THEME_MODE, value)
    suspend fun setTone(value: String) = set(TONE, value)
    suspend fun setToneAndDisableDynamicColor(value: String) {
        context.kemoDataStore.edit { values ->
            values[TONE] = value
            values[DYNAMIC_COLOR] = false
        }
    }

    suspend fun renameAccount(id: String, displayName: String) {
        context.kemoDataStore.edit { values ->
            val current = runCatching {
                json.decodeFromString<List<AccountConfig>>(values[ACCOUNTS] ?: "[]")
            }.getOrDefault(emptyList())
            values[ACCOUNTS] = json.encodeToString(
                current.map { account ->
                    if (account.id == id) account.copy(displayName = displayName.trim()) else account
                },
            )
        }
    }
    suspend fun setLanguage(value: String) = set(LANGUAGE, value)
    suspend fun setNotifications(value: Boolean) = set(NOTIFICATIONS, value)
    suspend fun setDynamicColor(value: Boolean) = set(DYNAMIC_COLOR, value)
    suspend fun setBiometricEnabled(value: Boolean) = set(BIOMETRIC_ENABLED, value)
    suspend fun setAutoLockMinutes(value: Int) = set(AUTO_LOCK, value)
    suspend fun setDownloadDirectoryUri(value: String) = set(DOWNLOAD_DIRECTORY_URI, value)
    suspend fun setThemeBackground(uri: String, mimeType: String) {
        context.kemoDataStore.edit { values ->
            values[THEME_BACKGROUND_URI] = uri
            values[THEME_BACKGROUND_MIME] = mimeType
        }
    }

    suspend fun resetTheme() {
        context.kemoDataStore.edit { values ->
            values[THEME_MODE] = "system"
            values[TONE] = "Purple"
            values[DYNAMIC_COLOR] = false
            values.remove(THEME_BACKGROUND_URI)
            values.remove(THEME_BACKGROUND_MIME)
        }
    }

    suspend fun setWidgetSummary(pending: Int, latest: String) {
        context.kemoDataStore.edit { values ->
            values[WIDGET_PENDING] = pending
            values[WIDGET_LATEST] = latest
        }
    }

    suspend fun saveChatState(accountId: String, historyJson: String, sessionId: String) {
        if (accountId.isBlank()) return
        context.kemoDataStore.edit { values ->
            val states = runCatching {
                json.decodeFromString<Map<String, AccountChatState>>(values[ACCOUNT_CHAT_STATES] ?: "{}")
            }.getOrDefault(emptyMap()).toMutableMap()
            states[accountId] = AccountChatState(historyJson, sessionId)
            values[ACCOUNT_CHAT_STATES] = json.encodeToString(states)
            values[LEGACY_CHAT_ACCOUNT] = accountId
            values[CHAT_HISTORY] = historyJson
            values[CHAT_SESSION_ID] = sessionId
        }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.kemoDataStore.edit { it[key] = value }
    }

    companion object {
        private val ACCOUNTS = stringPreferencesKey("accounts")
        private val CURRENT_ACCOUNT = stringPreferencesKey("current_account")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val TONE = stringPreferencesKey("tone")
        private val LANGUAGE = stringPreferencesKey("language")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val CHAT_HISTORY = stringPreferencesKey("chat_history")
        private val CHAT_SESSION_ID = stringPreferencesKey("session_id")
        private val ACCOUNT_CHAT_STATES = stringPreferencesKey("account_chat_states")
        private val LEGACY_CHAT_ACCOUNT = stringPreferencesKey("legacy_chat_account")
        private val AUTO_LOCK = intPreferencesKey("auto_lock_minutes")
        private val WIDGET_PENDING = intPreferencesKey("widget_pending")
        private val WIDGET_LATEST = stringPreferencesKey("widget_latest")
        private val DOWNLOAD_DIRECTORY_URI = stringPreferencesKey("download_directory_uri")
        private val THEME_BACKGROUND_URI = stringPreferencesKey("theme_background_uri")
        private val THEME_BACKGROUND_MIME = stringPreferencesKey("theme_background_mime")
    }
}
