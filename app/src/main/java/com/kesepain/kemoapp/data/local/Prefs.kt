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
data class AccountConfig(val id: String, val baseUrl: String, val username: String)

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
        AppPreferences(
            accounts = runCatching { json.decodeFromString<List<AccountConfig>>(values[ACCOUNTS] ?: "[]") }.getOrDefault(emptyList()),
            currentAccountId = values[CURRENT_ACCOUNT].orEmpty(),
            themeMode = values[THEME_MODE] ?: "system",
            tone = values[TONE] ?: "Purple",
            language = values[LANGUAGE] ?: "system",
            notifications = values[NOTIFICATIONS] ?: true,
            dynamicColor = values[DYNAMIC_COLOR] ?: false,
            biometricEnabled = values[BIOMETRIC_ENABLED] ?: false,
            chatHistoryJson = values[CHAT_HISTORY] ?: "[]",
            chatSessionId = values[CHAT_SESSION_ID].orEmpty(),
            autoLockMinutes = values[AUTO_LOCK] ?: 5,
            widgetPending = values[WIDGET_PENDING] ?: 0,
            widgetLatest = values[WIDGET_LATEST].orEmpty(),
            downloadDirectoryUri = values[DOWNLOAD_DIRECTORY_URI].orEmpty(),
            themeBackgroundUri = values[THEME_BACKGROUND_URI].orEmpty(),
            themeBackgroundMime = values[THEME_BACKGROUND_MIME].orEmpty(),
        )
    }

    suspend fun snapshot(): AppPreferences = flow.first()

    suspend fun saveAccount(account: AccountConfig) {
        context.kemoDataStore.edit { values ->
            val current = runCatching { json.decodeFromString<List<AccountConfig>>(values[ACCOUNTS] ?: "[]") }.getOrDefault(emptyList())
            val updated = current.filterNot { it.id == account.id } + account
            values[ACCOUNTS] = json.encodeToString(updated)
            values[CURRENT_ACCOUNT] = account.id
        }
    }

    suspend fun removeAccount(id: String) {
        context.kemoDataStore.edit { values ->
            val current = runCatching {
                json.decodeFromString<List<AccountConfig>>(values[ACCOUNTS] ?: "[]")
            }.getOrDefault(emptyList())
            val remaining = current.filterNot { it.id == id }
            values[ACCOUNTS] = json.encodeToString(remaining)
            if (values[CURRENT_ACCOUNT] == id) {
                values[CURRENT_ACCOUNT] = remaining.firstOrNull()?.id.orEmpty()
            }
        }
    }

    suspend fun setCurrentAccount(id: String) = set(CURRENT_ACCOUNT, id)
    suspend fun setThemeMode(value: String) = set(THEME_MODE, value)
    suspend fun setTone(value: String) = set(TONE, value)
    suspend fun setToneAndDisableDynamicColor(value: String) {
        context.kemoDataStore.edit { values ->
            values[TONE] = value
            values[DYNAMIC_COLOR] = false
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

    suspend fun saveChatState(historyJson: String, sessionId: String) {
        context.kemoDataStore.edit { values ->
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
        private val AUTO_LOCK = intPreferencesKey("auto_lock_minutes")
        private val WIDGET_PENDING = intPreferencesKey("widget_pending")
        private val WIDGET_LATEST = stringPreferencesKey("widget_latest")
        private val DOWNLOAD_DIRECTORY_URI = stringPreferencesKey("download_directory_uri")
        private val THEME_BACKGROUND_URI = stringPreferencesKey("theme_background_uri")
        private val THEME_BACKGROUND_MIME = stringPreferencesKey("theme_background_mime")
    }
}
