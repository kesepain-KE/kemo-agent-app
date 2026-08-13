package com.kesepain.kemoapp

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.kesepain.kemoapp.data.local.AccountConfig
import com.kesepain.kemoapp.data.local.AccountTransferCodec
import com.kesepain.kemoapp.data.local.AppPreferences
import com.kesepain.kemoapp.data.local.Prefs
import com.kesepain.kemoapp.data.local.SecureStore
import com.kesepain.kemoapp.chat.ChatKeepAliveService
import com.kesepain.kemoapp.data.remote.EventDto
import com.kesepain.kemoapp.data.remote.EventSocket
import com.kesepain.kemoapp.data.remote.ApiClient
import com.kesepain.kemoapp.data.repo.KemoRepository
import com.kesepain.kemoapp.data.stream.ChatEntry
import com.kesepain.kemoapp.data.stream.ChatAttachmentUi
import com.kesepain.kemoapp.data.stream.ChatMediaUi
import com.kesepain.kemoapp.data.stream.ChatRole
import com.kesepain.kemoapp.data.stream.ChatUsageUi
import com.kesepain.kemoapp.data.stream.GuidanceStatus
import com.kesepain.kemoapp.data.stream.StreamEvent
import com.kesepain.kemoapp.data.stream.ToolStatus
import com.kesepain.kemoapp.device.DeviceActionCommand
import com.kesepain.kemoapp.device.DeviceActionCoordinator
import com.kesepain.kemoapp.device.DeviceActionReporter
import com.kesepain.kemoapp.security.UnlockManager
import com.kesepain.kemoapp.update.AppAboutUiState
import com.kesepain.kemoapp.update.AppDownloadSource
import com.kesepain.kemoapp.update.AppUpdateRepository
import com.kesepain.kemoapp.update.AppUpdateUiState
import com.kesepain.kemoapp.update.ReleaseCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

data class FilePreviewUi(
    val scope: String,
    val path: String,
    val name: String,
    val mimeType: String,
    val extension: String,
    val bytes: ByteArray,
    val text: String = "",
)

private fun JsonElement?.fileListingPath(): String =
    (((this as? JsonObject)?.get("path")) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonElement?.fileListingPage(): Int =
    (((this as? JsonObject)?.get("pagination") as? JsonObject)?.get("page") as? JsonPrimitive)
        ?.contentOrNull?.toIntOrNull()?.coerceAtLeast(1) ?: 1

private fun appDeviceId(application: Application): String {
    val preferences = application.getSharedPreferences("kemo_device_identity", Context.MODE_PRIVATE)
    preferences.getString("device_id", "")?.takeIf(String::isNotBlank)?.let { return it }
    val androidId = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)
        .orEmpty().trim().takeIf(String::isNotBlank)
    val value = "android-${androidId ?: UUID.randomUUID()}".take(128)
    preferences.edit().putString("device_id", value).apply()
    return value
}

enum class UiMessageType { Info, Success, Error }

data class UiMessage(val text: String, val type: UiMessageType)

data class AppUiState(
    val preferences: AppPreferences = AppPreferences(),
    /** A saved account profile lets the user enter the local UI even when its session expired. */
    val hasSavedAccounts: Boolean = false,
    /** Session-only override used by the connection screen's direct-entry action. */
    val directEntry: Boolean = false,
    /** Changes for every background selection, including when a provider reuses the same URI. */
    val themeBackgroundRevision: Long = 0L,
    /** Monotonic signal consumed by the connection route after a successful login/save. */
    val connectionSuccessVersion: Long = 0L,
    val configured: Boolean = false,
    val unlocked: Boolean = false,
    val busy: Boolean = false,
    val error: String = "",
    val streaming: Boolean = false,
    val chatEntries: List<ChatEntry> = emptyList(),
    val chatSessionId: String = "",
    val chatClosed: Boolean = false,
    val conversations: JsonElement? = null,
    val tasks: JsonElement? = null,
    val cron: JsonElement? = null,
    val status: JsonElement? = null,
    val expands: JsonElement? = null,
    val senses: JsonElement? = null,
    val uploadFiles: JsonElement? = null,
    val generatedFiles: JsonElement? = null,
    val knowledge: JsonElement? = null,
    val models: JsonElement? = null,
    val modelCapabilities: JsonElement? = null,
    val agentConfig: JsonElement? = null,
    val avatarBytes: ByteArray? = null,
    val versions: JsonElement? = null,
    val filePreview: FilePreviewUi? = null,
    val rememberedDeviceToken: String = "",
    val rememberedUserPassword: String = "",
    val rememberCredentials: Boolean = false,
    val pendingChatAttachments: List<ChatAttachmentUi> = emptyList(),
    val chatAttachmentUploading: Boolean = false,
    val activeRunId: String = "",
    val guidanceSubmitting: Boolean = false,
    val chatStopping: Boolean = false,
)

private data class PendingNextTurnGuidance(
    val originRunId: String,
    val entryId: String,
    val text: String,
    val attachments: List<ChatAttachmentUi>,
    val reasoningEffort: String,
)

private data class ConversationTarget(
    val sessionId: String,
    val state: String,
    val rounds: Int,
)

private data class AccountRequestContext(
    val accountId: String,
    val epoch: Long,
)

private fun conversationTarget(payload: JsonElement): ConversationTarget? {
    val session = (payload as? JsonObject)?.get("session") as? JsonObject ?: return null
    val sessionId = (session["session_id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    if (sessionId.isBlank()) return null
    return ConversationTarget(
        sessionId = sessionId,
        state = (session["state"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "open" },
        rounds = (session["rounds"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )
}

private fun statusSessionId(payload: JsonElement): String {
    val root = payload as? JsonObject ?: return ""
    fun objectText(parent: String, key: String): String =
        (((root[parent] as? JsonObject)?.get(key)) as? JsonPrimitive)?.contentOrNull.orEmpty()
    return (root["resolved_session_id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        .ifBlank { objectText("overview", "session_id") }
        .ifBlank { objectText("runtime", "session_id") }
}

private fun latestOpenConversationTarget(payload: JsonElement): ConversationTarget? {
    val sessions = (payload as? JsonObject)?.get("sessions") as? JsonArray ?: return null
    return sessions.asSequence().mapNotNull { value ->
        val session = value as? JsonObject ?: return@mapNotNull null
        val sessionId = (session["session_id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val state = (session["state"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "open" }
        if (sessionId.isBlank() || state.equals("closed", ignoreCase = true)) return@mapNotNull null
        ConversationTarget(
            sessionId = sessionId,
            state = state,
            rounds = (session["rounds"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        )
    }.firstOrNull()
}

class MainViewModel(application: Application) : AndroidViewModel(application), DefaultLifecycleObserver {
    private val prefs = Prefs(application)
    private val secure = SecureStore(application)
    private val repo = KemoRepository(application)
    private val deviceId = appDeviceId(application)
    private val _state = MutableStateFlow(AppUiState())
    private val _messages = MutableSharedFlow<UiMessage>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _pendingKeys = MutableStateFlow<Set<String>>(emptySet())
    private val appUpdateRepository = AppUpdateRepository(application)
    private val _appAbout = MutableStateFlow(AppAboutUiState())
    private val unlockManager = UnlockManager { _state.value.preferences.autoLockMinutes }
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()
    val pendingKeys: StateFlow<Set<String>> = _pendingKeys.asStateFlow()
    val appAbout: StateFlow<AppAboutUiState> = _appAbout.asStateFlow()
    private var eventSocket: EventSocket? = null
    private var observedAccountId: String? = null
    private var accountEpoch = 0L
    private var accountRestoreJob: Job? = null
    private var reconnectingAccountId: String = ""
    private var lastForegroundReconnectAt: Long = 0L
    private var chatJob: Job? = null
    private var persistChatJob: Job? = null
    private var pendingNextTurnGuidance: PendingNextTurnGuidance? = null
    private val recentNotifications = mutableMapOf<String, Long>()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        viewModelScope.launch {
            prefs.flow.collectLatest { values ->
                val account = values.accounts.firstOrNull { it.id == values.currentAccountId }
                val credentials = account?.let { repo.credentialState(it.id) }
                val configured = credentials?.sessionToken?.isNotBlank() == true && credentials.deviceToken.isNotBlank()
                val hasSavedAccounts = values.accounts.isNotEmpty()
                val accountId = account?.id.orEmpty()
                val accountChanged = observedAccountId != accountId
                if (accountChanged) {
                    observedAccountId = accountId
                    accountEpoch += 1L
                    accountRestoreJob?.cancel()
                }
                _state.update { current ->
                    if (accountChanged) {
                        val restored = runCatching {
                            ApiClient.json.decodeFromString<List<ChatEntry>>(values.chatHistoryJson)
                        }.getOrDefault(emptyList()).takeLast(100)
                        current.copy(
                            preferences = values,
                            hasSavedAccounts = hasSavedAccounts,
                            configured = configured,
                            chatEntries = restored,
                            chatSessionId = values.chatSessionId.ifBlank { "app-${UUID.randomUUID()}" },
                            chatClosed = false,
                            streaming = false,
                            activeRunId = "",
                            guidanceSubmitting = false,
                            chatStopping = false,
                            pendingChatAttachments = emptyList(),
                            chatAttachmentUploading = false,
                            conversations = null,
                            tasks = null,
                            cron = null,
                            status = null,
                            expands = null,
                            senses = null,
                            uploadFiles = null,
                            generatedFiles = null,
                            knowledge = null,
                            models = null,
                            modelCapabilities = null,
                            agentConfig = null,
                            avatarBytes = null,
                            versions = null,
                            filePreview = null,
                            error = "",
                            rememberedDeviceToken = credentials?.deviceToken.takeIf { credentials?.rememberCredentials == true }.orEmpty(),
                            rememberedUserPassword = credentials?.userPassword.orEmpty(),
                            rememberCredentials = credentials?.rememberCredentials == true,
                        )
                    } else current.copy(
                        preferences = values,
                        hasSavedAccounts = hasSavedAccounts,
                        configured = configured,
                        rememberedDeviceToken = credentials?.deviceToken.takeIf { credentials?.rememberCredentials == true }.orEmpty(),
                        rememberedUserPassword = credentials?.userPassword.orEmpty(),
                        rememberCredentials = credentials?.rememberCredentials == true,
                    )
                }
                if (accountChanged && account != null) {
                    val expectedEpoch = accountEpoch
                    scheduleAccountReconnect(account.id, expectedEpoch, reportFailure = false)
                }
                if (values.chatSessionId.isBlank() && _state.value.chatSessionId.isNotBlank()) {
                    persistChatNow(accountId)
                }
                if (account != null && secure.get(account.id, SecureStore.APP_PASSWORD_HASH).isNotBlank()) {
                    // Security is configured; remain locked until explicit authentication.
                } else if (hasSavedAccounts) {
                    unlockManager.unlock()
                }
            }
        }
        viewModelScope.launch { unlockManager.unlocked.collectLatest { unlocked -> _state.update { it.copy(unlocked = unlocked) }; if (unlocked) startSocket() } }
        repo.onSessionExpired = { expiredAccountId ->
            viewModelScope.launch {
                repo.clearSession(expiredAccountId)
                if (_state.value.preferences.currentAccountId == expiredAccountId) {
                    eventSocket?.stop()
                    _state.update { it.copy(configured = false) }
                    scheduleAccountReconnect(expiredAccountId, accountEpoch, reportFailure = true)
                }
            }
        }
    }

    /**
     * Revalidates the selected account whenever the process returns to the foreground.
     * The bridge keeps sessions in memory, so a framework/bridge restart can invalidate
     * an otherwise remembered App session while the encrypted device token and password
     * remain usable. Re-authenticating here avoids requiring an edit-and-save round trip.
     */
    override fun onStart(owner: LifecycleOwner) {
        val now = System.currentTimeMillis()
        if (now - lastForegroundReconnectAt < FOREGROUND_RECONNECT_COOLDOWN_MS) return
        lastForegroundReconnectAt = now
        val accountId = _state.value.preferences.currentAccountId
        if (accountId.isNotBlank()) {
            scheduleAccountReconnect(accountId, accountEpoch, reportFailure = false)
        }
    }

    fun enterAppDirectly() {
        _state.update { it.copy(directEntry = true, error = "") }
        unlockManager.unlock()
    }

    fun connect(displayName: String, baseUrl: String, deviceToken: String, username: String, password: String, appPassword: String, rememberCredentials: Boolean) {
        connectInternal(null, displayName, baseUrl, deviceToken, username, password, appPassword, rememberCredentials)
    }

    fun reconnectAccount(accountId: String, displayName: String, baseUrl: String, deviceToken: String, username: String, password: String, appPassword: String, rememberCredentials: Boolean) {
        connectInternal(accountId, displayName, baseUrl, deviceToken, username, password, appPassword, rememberCredentials)
    }

    private fun connectInternal(replaceAccountId: String?, displayName: String, baseUrl: String, deviceToken: String, username: String, password: String, appPassword: String, rememberCredentials: Boolean) {
        launchBusy {
            val existing = _state.value.preferences.accounts.firstOrNull { it.baseUrl == baseUrl.trimEnd('/') && it.username == username }
                ?: replaceAccountId?.let { id -> _state.value.preferences.accounts.firstOrNull { it.id == id } }
            val effectiveToken = deviceToken.ifBlank { existing?.let { repo.credentialState(it.id).deviceToken }.orEmpty() }
            require(effectiveToken.isNotBlank()) { "device token is required" }
            val connectedAccount = repo.login(displayName, baseUrl, effectiveToken, username, password, appPassword, rememberCredentials)
            if (replaceAccountId != null && replaceAccountId != connectedAccount.id) repo.deleteAccount(replaceAccountId)
            unlockManager.unlock()
            _state.update { current ->
                current.copy(
                    connectionSuccessVersion = current.connectionSuccessVersion + 1L,
                    error = "",
                )
            }
            restoreAccountChat(connectedAccount.id, accountEpoch)
            loadDashboard()
        }
    }

    fun unlockWithPassword(value: String) {
        viewModelScope.launch {
            val account = currentAccount() ?: return@launch
            if (repo.verifyAppPassword(account.id, value)) unlockManager.unlock() else _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) }
        }
    }

    fun unlockWithBiometric() { unlockManager.unlock() }
    fun lock() { unlockManager.lock() }

    fun sendChat(prompt: String, reasoningEffort: String) {
        val snapshot = _state.value
        val pendingAttachments = snapshot.pendingChatAttachments
        if (!snapshot.configured || (prompt.isBlank() && pendingAttachments.isEmpty()) || snapshot.chatAttachmentUploading) return
        if (snapshot.streaming) {
            submitGuidance(prompt, reasoningEffort, pendingAttachments)
        } else if (!snapshot.chatClosed) {
            startChat(prompt, reasoningEffort, pendingAttachments)
        }
    }

    private fun submitGuidance(prompt: String, reasoningEffort: String, attachments: List<ChatAttachmentUi>) {
        val snapshot = _state.value
        val runId = snapshot.activeRunId
        if (!snapshot.streaming || runId.isBlank() || snapshot.guidanceSubmitting) return
        val entryId = "guidance-${UUID.randomUUID()}"
        val entry = ChatEntry(
            id = entryId,
            role = ChatRole.GUIDANCE,
            text = prompt,
            attachments = attachments,
            guidanceStatus = if (snapshot.chatStopping) GuidanceStatus.QUEUED else GuidanceStatus.SUBMITTING,
        )
        _state.update {
            it.copy(
                chatEntries = (it.chatEntries + entry).takeLast(100),
                pendingChatAttachments = emptyList(),
                guidanceSubmitting = !snapshot.chatStopping,
                error = "",
            )
        }
        if (snapshot.chatStopping) {
            pendingNextTurnGuidance = PendingNextTurnGuidance(runId, entryId, prompt, attachments, reasoningEffort)
            emitMessage(R.string.feedback_guidance_queued, UiMessageType.Info)
            scheduleChatPersist()
            return
        }
        scheduleChatPersist()
        viewModelScope.launch {
            runCatching {
                repo.submitGuidance(runId, prompt, entryId, attachments.map { it.path })
            }.onSuccess { value ->
                val status = ((value as? JsonObject)?.get("status") as? JsonPrimitive)?.contentOrNull.orEmpty()
                val queued = status == "queued_next_turn"
                if (queued) {
                    pendingNextTurnGuidance = PendingNextTurnGuidance(runId, entryId, prompt, attachments, reasoningEffort)
                }
                _state.update { current ->
                    current.copy(
                        guidanceSubmitting = false,
                        chatEntries = current.chatEntries.map { item ->
                            if (item.id == entryId) item.copy(guidanceStatus = if (queued) GuidanceStatus.QUEUED else GuidanceStatus.ACCEPTED) else item
                        },
                    )
                }
                emitMessage(if (queued) R.string.feedback_guidance_queued else R.string.feedback_guidance_sent, UiMessageType.Success)
            }.onFailure { failure ->
                if (failure.message.orEmpty().contains("(404)")) {
                    pendingNextTurnGuidance = PendingNextTurnGuidance(runId, entryId, prompt, attachments, reasoningEffort)
                    _state.update { current ->
                        current.copy(
                            guidanceSubmitting = false,
                            chatEntries = current.chatEntries.map { item ->
                                if (item.id == entryId) item.copy(guidanceStatus = GuidanceStatus.QUEUED) else item
                            },
                        )
                    }
                    emitMessage(R.string.feedback_guidance_queued, UiMessageType.Info)
                } else {
                    _state.update { current ->
                        current.copy(
                            guidanceSubmitting = false,
                            pendingChatAttachments = (current.pendingChatAttachments + attachments).distinctBy { it.path },
                            chatEntries = current.chatEntries.map { item ->
                                if (item.id == entryId) item.copy(guidanceStatus = GuidanceStatus.ERROR) else item
                            },
                            error = getApplication<Application>().getString(R.string.error_generic),
                        )
                    }
                    emitMessage(R.string.feedback_guidance_failed, UiMessageType.Error)
                }
            }
            scheduleChatPersist()
        }
    }

    fun stopChat() {
        val snapshot = _state.value
        val runId = snapshot.activeRunId
        if (!snapshot.streaming || snapshot.chatStopping || runId.isBlank()) return
        _state.update { it.copy(chatStopping = true, error = "") }
        viewModelScope.launch {
            runCatching { repo.cancelRun(runId) }
                .onSuccess { emitMessage(R.string.feedback_stop_requested, UiMessageType.Info) }
                .onFailure {
                    _state.update { current -> current.copy(chatStopping = false, error = getApplication<Application>().getString(R.string.error_generic)) }
                    emitMessage(R.string.feedback_stop_failed, UiMessageType.Error)
                }
        }
    }

    private fun startChat(
        prompt: String,
        reasoningEffort: String,
        pendingAttachments: List<ChatAttachmentUi>,
        reuseUserEntryId: String? = null,
    ) {
        val assistantId = UUID.randomUUID().toString()
        val sessionId = _state.value.chatSessionId.ifBlank { "app-${UUID.randomUUID()}" }
        val startedAt = System.currentTimeMillis()
        val userEntry = ChatEntry(reuseUserEntryId ?: "user-$startedAt", ChatRole.USER, text = prompt, attachments = pendingAttachments)
        val assistantEntry = ChatEntry(assistantId, ChatRole.ASSISTANT, startedAtMs = startedAt)
        _state.update {
            val entries = if (reuseUserEntryId == null) {
                it.chatEntries + userEntry
            } else {
                it.chatEntries.map { entry -> if (entry.id == reuseUserEntryId) userEntry else entry }
            }
            it.copy(
                chatEntries = (entries + assistantEntry).takeLast(100),
                chatSessionId = sessionId,
                chatClosed = false,
                streaming = true,
                activeRunId = assistantId,
                chatStopping = false,
                guidanceSubmitting = false,
                error = "",
                pendingChatAttachments = emptyList(),
            )
        }
        scheduleChatPersist()
        // This request is initiated while the App is visible. Promote the process
        // before launching the stream so Android/ColorOS cannot suspend the socket
        // merely because the user backgrounds the Activity or turns the screen off.
        ChatKeepAliveService.start(getApplication())
        chatJob = viewModelScope.launch {
            try {
                var streamFailed = false
                val result = runCatching {
                    coroutineScope {
                        val events = Channel<StreamEvent>(Channel.UNLIMITED)
                        val renderer = launch {
                            while (true) {
                                val first = events.receiveCatching().getOrNull() ?: break
                                val batch = mutableListOf(first)
                                delay(STREAM_RENDER_INTERVAL_MS)
                                while (true) batch += events.tryReceive().getOrNull() ?: break
                                applyStreamEvents(assistantId, batch)
                            }
                        }
                        try {
                            repo.streamChat(
                                prompt,
                                sessionId,
                                assistantId,
                                conversationClientId(),
                                pendingAttachments.map { it.path },
                                reasoningEffort,
                            ) { event ->
                                if (event is StreamEvent.Error) streamFailed = true
                                events.trySend(event)
                            }
                        } finally {
                            events.close()
                            renderer.join()
                        }
                    }
                }
                val wasStopping = _state.value.chatStopping && _state.value.activeRunId == assistantId
                val failed = (result.isFailure || streamFailed) && !wasStopping
                val failureText = getApplication<Application>().getString(R.string.chat_transport_failed)
                val emptyText = getApplication<Application>().getString(R.string.chat_empty_response)
                val stoppedText = getApplication<Application>().getString(R.string.chat_stopped)
                val finishedAt = System.currentTimeMillis()
                _state.update { current ->
                    current.copy(
                        streaming = false,
                        activeRunId = if (current.activeRunId == assistantId) "" else current.activeRunId,
                        chatStopping = false,
                        guidanceSubmitting = false,
                        error = if (failed) failureText else current.error,
                        chatEntries = current.chatEntries.map { entry ->
                            if (entry.role == ChatRole.GUIDANCE && entry.guidanceStatus == GuidanceStatus.ACCEPTED) {
                                entry.copy(guidanceStatus = GuidanceStatus.COMPLETED)
                            } else if (entry.id != assistantId) entry
                            else {
                                val hasVisibleContent = entry.text.isNotBlank() || entry.reasoning.isNotBlank() || entry.tools.isNotEmpty() || entry.media.isNotEmpty()
                                entry.copy(
                                    text = when {
                                        failed -> failureText
                                        wasStopping && !hasVisibleContent -> stoppedText
                                        !hasVisibleContent -> emptyText
                                        else -> entry.text
                                    },
                                    tools = entry.tools.map { tool ->
                                        if (tool.status == ToolStatus.RUNNING) tool.copy(
                                            status = ToolStatus.FAILED,
                                            elapsedMs = (finishedAt - tool.startedAtMs).coerceAtLeast(0),
                                        ) else tool
                                    },
                                    usage = (entry.usage ?: ChatUsageUi()).copy(
                                        elapsedMs = entry.usage?.elapsedMs?.takeIf { it > 0 }
                                            ?: (finishedAt - entry.startedAtMs).coerceAtLeast(0),
                                    ),
                                )
                            }
                        }
                    )
                }
                if (failed) {
                    _messages.emit(UiMessage(failureText, UiMessageType.Error))
                } else if (!wasStopping) {
                    val reply = _state.value.chatEntries.firstOrNull { it.id == assistantId }?.text.orEmpty()
                    postNotification(
                        key = "conversation:$sessionId",
                        channel = KemoApp.CHAT_CHANNEL,
                        title = getApplication<Application>().getString(R.string.notification_chat_complete_title),
                        text = getApplication<Application>().getString(
                            R.string.notification_chat_complete_body,
                            prompt.take(48).ifBlank { reply.take(48).ifBlank { getApplication<Application>().getString(R.string.app_name) } },
                        ),
                        openTasks = false,
                    )
                }
                loadStatusInternal()
                persistChatNow()
                val queued = pendingNextTurnGuidance?.takeIf { it.originRunId == assistantId }
                if (queued != null) pendingNextTurnGuidance = null
                if (queued != null) {
                    startChat(
                        prompt = queued.text,
                        reasoningEffort = queued.reasoningEffort,
                        pendingAttachments = queued.attachments,
                        reuseUserEntryId = queued.entryId,
                    )
                } else {
                    chatJob = null
                }
            } finally {
                // A queued guidance turn sets streaming=true before this finally
                // block, so it keeps the same foreground interval without a gap.
                if (!_state.value.streaming) ChatKeepAliveService.stop(getApplication())
            }
        }
    }

    fun retryLastResponse(reasoningEffort: String) {
        val snapshot = _state.value
        if (snapshot.streaming || snapshot.chatClosed || snapshot.chatAttachmentUploading) return
        val userIndex = snapshot.chatEntries.indexOfLast { it.role == ChatRole.USER }
        val lastUser = snapshot.chatEntries.getOrNull(userIndex)
        val sessionId = snapshot.chatSessionId
        if (userIndex < 0 || lastUser == null || lastUser.text.isBlank() || sessionId.isBlank()) {
            emitMessage(R.string.feedback_regenerate_unavailable, UiMessageType.Info)
            return
        }
        val expectedRound = currentConversationRound().coerceAtLeast(1)
        launchBusy("chat:retry") {
            repo.undoLastRound(sessionId, expectedRound, lastUser.text)
            _state.update { current ->
                current.copy(
                    chatEntries = current.chatEntries.take(userIndex),
                    chatClosed = false,
                    pendingChatAttachments = lastUser.attachments,
                    error = "",
                )
            }
            persistChatNow()
            startChat(lastUser.text, reasoningEffort, lastUser.attachments)
        }
    }

    fun reportCopied() = emitMessage(R.string.feedback_copied, UiMessageType.Success)

    fun addChatAttachment(uri: Uri) {
        if (_state.value.chatAttachmentUploading) return
        viewModelScope.launch {
            _state.update { it.copy(chatAttachmentUploading = true, error = "") }
            runCatching {
                require(_state.value.pendingChatAttachments.size < 20) { "单轮最多添加 20 个文件" }
                val sessionId = _state.value.chatSessionId.ifBlank { "app-${UUID.randomUUID()}" }
                val result = repo.uploadFile(uri, "app-chat/$sessionId")
                val root = result as? JsonObject ?: error("上传响应无效")
                val path = (root["path"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                require(path.isNotBlank()) { "上传响应缺少文件路径" }
                val name = path.substringAfterLast('/').substringAfterLast('\\')
                val mimeType = (root["mime_type"] as? JsonPrimitive)?.contentOrNull
                    ?: getApplication<Application>().contentResolver.getType(uri)
                    ?: "application/octet-stream"
                val mediaKind = (root["media_kind"] as? JsonPrimitive)?.contentOrNull
                    ?: when {
                        mimeType.startsWith("image/") -> "image"
                        mimeType.startsWith("audio/") -> "audio"
                        mimeType.startsWith("video/") -> "video"
                        else -> "file"
                    }
                val size = (root["size"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
                _state.update { current ->
                    current.copy(
                        pendingChatAttachments = (
                            current.pendingChatAttachments + ChatAttachmentUi(
                                name = name,
                                path = path,
                                mimeType = mimeType,
                                mediaKind = mediaKind,
                                size = size,
                                localUri = uri.toString(),
                            )
                            ).distinctBy { it.path },
                    )
                }
            }.onFailure {
                _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) }
                emitMessage(R.string.feedback_attachment_failed, UiMessageType.Error)
            }
            _state.update { it.copy(chatAttachmentUploading = false) }
        }
    }

    fun removeChatAttachment(path: String) = _state.update { current ->
        current.copy(pendingChatAttachments = current.pendingChatAttachments.filterNot { it.path == path })
    }

    private fun applyStreamEvents(entryId: String, events: List<StreamEvent>) {
        if (events.isEmpty()) return
        _state.update { current ->
            current.copy(chatEntries = current.chatEntries.map { entry ->
                if (entry.id != entryId) entry else events.fold(entry, ::reduceStreamEvent)
            })
        }
        scheduleChatPersist()
    }

    private fun reduceStreamEvent(entry: ChatEntry, event: StreamEvent): ChatEntry = when (event) {
        is StreamEvent.Reasoning -> entry.copy(reasoning = entry.reasoning + event.text)
        is StreamEvent.Text -> entry.copy(text = entry.text + event.text)
        is StreamEvent.ToolStart -> entry.copy(
            tools = entry.tools.filterNot { it.callId == event.call.callId } + event.call.copy(startedAtMs = System.currentTimeMillis()),
        )
        is StreamEvent.ToolEnd -> entry.copy(tools = entry.tools.map { tool ->
            if (tool.callId == event.callId) tool.copy(
                status = event.status,
                resultPreview = event.resultPreview,
                elapsedMs = event.elapsedMs.takeIf { it > 0 }
                    ?: (System.currentTimeMillis() - tool.startedAtMs).coerceAtLeast(0),
            ) else tool
        })
        is StreamEvent.Media -> entry.copy(media = (entry.media + event.value).distinctBy { "${it.assetId}:${it.path}" })
        is StreamEvent.Usage -> entry.copy(usage = event.value)
        is StreamEvent.Error -> entry.copy(
            text = getApplication<Application>().getString(R.string.chat_transport_failed),
            tools = entry.tools.map {
                if (it.status == ToolStatus.RUNNING) it.copy(
                    status = ToolStatus.FAILED,
                    elapsedMs = (System.currentTimeMillis() - it.startedAtMs).coerceAtLeast(0),
                ) else it
            },
        )
        is StreamEvent.Done -> event.value?.let { entry.copy(usage = it) } ?: entry
    }

    fun loadChatMedia(assetId: String, path: String) {
        val media = _state.value.chatEntries.asSequence()
            .flatMap { it.media.asSequence() }
            .firstOrNull { it.assetId == assetId && it.path == path }
            ?: return
        if (media.loading || media.localUri.isNotBlank()) return
        _state.update { current ->
            current.copy(chatEntries = current.chatEntries.map { entry ->
                entry.copy(media = entry.media.map { item ->
                    if (item.assetId == assetId && item.path == path) item.copy(loading = true, error = "") else item
                })
            })
        }
        viewModelScope.launch {
            runCatching { repo.cacheChatMedia(path, media.name) }
                .onSuccess { uri ->
                    _state.update { current ->
                        current.copy(chatEntries = current.chatEntries.map { entry ->
                            entry.copy(media = entry.media.map { item ->
                                if (item.assetId == assetId && item.path == path) item.copy(localUri = uri, loading = false, error = "") else item
                            })
                        })
                    }
                    scheduleChatPersist()
                }
                .onFailure {
                    _state.update { current ->
                        current.copy(chatEntries = current.chatEntries.map { entry ->
                            entry.copy(media = entry.media.map { item ->
                                if (item.assetId == assetId && item.path == path) item.copy(loading = false, error = getApplication<Application>().getString(R.string.media_preview_failed)) else item
                            })
                        })
                    }
                    emitMessage(R.string.media_preview_failed, UiMessageType.Error)
                }
        }
    }

    fun loadChatAttachment(path: String) {
        val attachment = _state.value.chatEntries.asSequence()
            .flatMap { it.attachments.asSequence() }
            .firstOrNull { it.path == path }
            ?: return
        if (attachment.loading || attachment.localUri.isNotBlank()) return
        _state.update { current ->
            current.copy(chatEntries = current.chatEntries.map { entry ->
                entry.copy(attachments = entry.attachments.map { item ->
                    if (item.path == path) item.copy(loading = true, error = "") else item
                })
            })
        }
        viewModelScope.launch {
            runCatching { repo.cacheChatMedia(path, attachment.name, scope = "upload") }
                .onSuccess { uri ->
                    _state.update { current ->
                        current.copy(chatEntries = current.chatEntries.map { entry ->
                            entry.copy(attachments = entry.attachments.map { item ->
                                if (item.path == path) item.copy(localUri = uri, loading = false, error = "") else item
                            })
                        })
                    }
                    scheduleChatPersist()
                }
                .onFailure {
                    val message = getApplication<Application>().getString(R.string.media_preview_failed)
                    _state.update { current ->
                        current.copy(chatEntries = current.chatEntries.map { entry ->
                            entry.copy(attachments = entry.attachments.map { item ->
                                if (item.path == path) item.copy(loading = false, error = message) else item
                            })
                        })
                    }
                    emitMessage(R.string.media_preview_failed, UiMessageType.Error)
                }
        }
    }

    private fun currentConversationRound(): Int {
        val localRounds = _state.value.chatEntries.count { it.role == ChatRole.USER }
        val root = _state.value.status as? JsonObject
        val runtime = root?.get("runtime") as? JsonObject
        val runtimeContext = runtime?.get("context") as? JsonObject
        val overview = root?.get("overview") as? JsonObject
        val overviewContext = overview?.get("context") as? JsonObject
        val reportedRounds = listOfNotNull(
            (runtimeContext?.get("rounds") as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            (overviewContext?.get("rounds") as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            (overviewContext?.get("session_total_rounds") as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
        ).maxOrNull() ?: 0
        return maxOf(localRounds, reportedRounds)
    }

    private fun conversationClientId(): String = "app_$deviceId".take(128)

    fun newConversation() {
        if (_state.value.streaming) return
        resetConversationState()
        viewModelScope.launch { persistChatNow() }
        // Ask the bridge for the fresh session snapshot immediately. This keeps
        // the panel populated with zero/initial values without waiting for the
        // next outbound chat request to trigger a status refresh.
        viewModelScope.launch { loadStatusInternal() }
    }

    private fun resetConversationState() {
        _state.update {
            it.copy(
                chatEntries = emptyList(),
                chatSessionId = "app-${UUID.randomUUID()}",
                chatClosed = false,
                pendingChatAttachments = emptyList(),
                chatAttachmentUploading = false,
                activeRunId = "",
                guidanceSubmitting = false,
                chatStopping = false,
                // Runtime/context telemetry belongs to the conversation session.
                // Clear it synchronously so the header and context sheet never
                // display data from the discarded session.
                status = null,
                error = "",
            )
        }
    }

    fun clearConversation() {
        if (_state.value.streaming) return
        launchBusy(
            key = "conversation:clear",
            successMessage = R.string.feedback_conversation_cleared,
        ) {
            val sessionId = _state.value.chatSessionId
            val hasConversation = sessionId.isNotBlank() &&
                (_state.value.chatEntries.isNotEmpty() || currentConversationRound() > 0)
            // "Clear" discards the current server-side conversation. Previously it
            // only replaced the local list, so the next status request resolved the
            // still-active server session and immediately restored every message.
            if (hasConversation) repo.deleteConversation(sessionId, conversationClientId())
            resetConversationState()
            persistChatNow()
            loadStatusInternal()
            loadConversationsInternal()
        }
    }

    fun saveConversation() = launchBusy {
        val sessionId = _state.value.chatSessionId
        require(sessionId.isNotBlank() && _state.value.chatEntries.isNotEmpty()) { "当前没有可保存的对话" }
        repo.closeConversation(sessionId, conversationClientId())
        _state.update { it.copy(chatClosed = true) }
        persistChatNow()
        loadConversationsInternal()
    }

    fun compressConversation() = launchBusy {
        val sessionId = _state.value.chatSessionId
        require(sessionId.isNotBlank() && _state.value.chatEntries.isNotEmpty()) { "当前没有可压缩的对话" }
        repo.compressConversation(sessionId)
    }

    fun saveAndNewConversation() = launchBusy {
        val sessionId = _state.value.chatSessionId
        if (sessionId.isNotBlank() && _state.value.chatEntries.isNotEmpty()) {
            repo.closeConversation(sessionId, conversationClientId())
        }
        _state.update { it.copy(chatEntries = emptyList(), chatSessionId = "app-${UUID.randomUUID()}", chatClosed = false, status = null) }
        persistChatNow()
        loadStatusInternal()
        loadConversationsInternal()
    }

    fun switchConversation(sessionId: String) = launchBusy {
        require(sessionId.isNotBlank()) { "会话标识为空" }
        val payload = repo.conversationMessages(sessionId)
        val entries = parseHistoryMessages(payload, sessionId)
        _state.update { it.copy(chatEntries = entries.takeLast(100), chatSessionId = sessionId, chatClosed = true, status = null, error = "") }
        loadStatusInternal()
        persistChatNow()
    }

    fun deleteConversation(sessionId: String) = launchBusy {
        repo.deleteConversation(sessionId, conversationClientId())
        if (_state.value.chatSessionId == sessionId) {
            _state.update { it.copy(chatEntries = emptyList(), chatSessionId = "app-${UUID.randomUUID()}", chatClosed = false, status = null) }
            persistChatNow()
            loadStatusInternal()
        }
        loadConversationsInternal()
    }

    fun deleteAllConversations() = launchBusy {
        repo.deleteAllConversations()
        _state.update { it.copy(chatEntries = emptyList(), chatSessionId = "app-${UUID.randomUUID()}", chatClosed = false, conversations = null, status = null) }
        persistChatNow()
        loadStatusInternal()
        loadConversationsInternal()
    }

    fun loadDashboard() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            loadTasksInternal()
            loadStatusInternal()
            loadModulesInternal()
            loadAgentConfigInternal()
        }
    }

    fun loadTasks() = launchBusy("refresh:tasks") { loadTasksInternal() }
    fun loadConversations() = viewModelScope.launch { loadConversationsInternal() }
    fun loadStatus() = launchBusy("refresh:status") { loadStatusInternal() }
    fun loadModules() = launchBusy("refresh:modules") { loadModulesInternal() }
    fun loadFiles(scope: String, path: String, page: Int = 1) = launchBusy("refresh:files:${scope.lowercase()}") { loadFileDirectory(scope, path, page) }
    fun uploadFile(uri: Uri, directory: String) = launchBusy("upload", R.string.feedback_uploaded) {
        repo.uploadFile(uri, directory)
        loadFileDirectory("upload", directory, 1)
    }
    fun loadKnowledge() = viewModelScope.launch { loadValue({ repo.knowledge() }) { value -> copy(knowledge = value) } }
    fun loadModels(refresh: Boolean = false) = launchBusy("refresh:models") { loadValue({ repo.models(refresh) }) { value -> copy(models = value) } }
    fun loadAgentConfig() = launchBusy("refresh:config") { loadAgentConfigInternal() }
    fun loadModelCapabilities(model: String, refresh: Boolean = false) {
        val normalized = model.trim()
        if (normalized.isBlank()) {
            _state.update { it.copy(modelCapabilities = null) }
            return
        }
        val loadedModel = ((_state.value.modelCapabilities as? JsonObject)?.get("model") as? JsonPrimitive)
            ?.contentOrNull.orEmpty()
        if (!refresh && loadedModel == normalized) return
        launchBusy("refresh:model-capabilities") {
            loadValue({ repo.modelCapabilities(normalized, refresh) }) { value ->
                copy(modelCapabilities = value)
            }
        }
    }
    fun loadProfileData() = viewModelScope.launch {
        val requestContext = accountRequestContext()
        runCatching { repo.avatar() to repo.version() }
            .onSuccess { values ->
                if (isCurrentAccountRequest(requestContext)) {
                    _state.update { it.copy(avatarBytes = values.first, versions = values.second, error = "") }
                }
            }
            .onFailure {
                if (isCurrentAccountRequest(requestContext)) {
                    _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) }
                }
            }
    }

    fun loadAppAbout() {
        if (_appAbout.value.avatarBytes != null || _appAbout.value.avatarLoading) return
        _appAbout.update { it.copy(avatarLoading = true) }
        viewModelScope.launch {
            val avatar = runCatching { appUpdateRepository.loadGitHubAvatar() }.getOrNull()
            _appAbout.update { it.copy(avatarBytes = avatar, avatarLoading = false) }
        }
    }

    private fun scheduleAccountReconnect(
        accountId: String,
        expectedEpoch: Long,
        reportFailure: Boolean,
    ) {
        if (accountId.isBlank() || reconnectingAccountId == accountId) return
        accountRestoreJob?.cancel()
        reconnectingAccountId = accountId
        accountRestoreJob = viewModelScope.launch {
            try {
                val credentials = runCatching { repo.ensureAccountSession(accountId) }
                    .getOrElse {
                        if (_state.value.preferences.currentAccountId == accountId && accountEpoch == expectedEpoch) {
                            val usableSession = repo.credentialState(accountId).let {
                                it.deviceToken.isNotBlank() && it.sessionToken.isNotBlank()
                            }
                            _state.update { current -> current.copy(configured = usableSession) }
                            if (reportFailure) {
                                val message = getApplication<Application>().getString(R.string.feedback_account_reconnect_required)
                                _state.update { current -> current.copy(error = message) }
                                _messages.emit(UiMessage(message, UiMessageType.Error))
                            }
                        }
                        return@launch
                    }
                if (_state.value.preferences.currentAccountId != accountId || accountEpoch != expectedEpoch) return@launch
                _state.update {
                    it.copy(
                        configured = credentials.deviceToken.isNotBlank() && credentials.sessionToken.isNotBlank(),
                        error = "",
                    )
                }
                restoreAccountChat(accountId, expectedEpoch)
                if (_state.value.unlocked) startSocket()
                loadTasksInternal()
                loadModulesInternal()
            } finally {
                if (reconnectingAccountId == accountId) reconnectingAccountId = ""
            }
        }
    }

    private suspend fun restoreAccountChat(accountId: String, expectedEpoch: Long) {
        if (accountId.isBlank()) return
        val clientId = conversationClientId()
        val target = runCatching {
            conversationTarget(repo.activeConversation(clientId))
        }.getOrNull() ?: runCatching {
            latestOpenConversationTarget(repo.conversations(limit = 1))
        }.getOrNull() ?: ConversationTarget(
            sessionId = "app-${UUID.randomUUID()}",
            state = "open",
            rounds = 0,
        )
        if (target.sessionId.isBlank()) return
        val entries = if (target.rounds > 0) {
            runCatching {
                parseHistoryMessages(repo.conversationMessages(target.sessionId), target.sessionId)
            }.getOrNull()
        } else {
            emptyList()
        }
        if (_state.value.preferences.currentAccountId != accountId || accountEpoch != expectedEpoch) return
        val restoredEntries = entries ?: if (_state.value.preferences.chatSessionId == target.sessionId) {
            runCatching {
                ApiClient.json.decodeFromString<List<ChatEntry>>(_state.value.preferences.chatHistoryJson)
            }.getOrDefault(emptyList()).takeLast(100)
        } else {
            emptyList()
        }
        _state.update { current ->
            if (current.preferences.currentAccountId != accountId || accountEpoch != expectedEpoch) current
            else current.copy(
                chatEntries = restoredEntries.takeLast(100),
                chatSessionId = target.sessionId,
                chatClosed = target.state.equals("closed", ignoreCase = true),
                streaming = false,
                activeRunId = "",
                guidanceSubmitting = false,
                chatStopping = false,
                status = null,
                error = "",
            )
        }
        persistChatNow(accountId)
        loadAgentConfigForAccount(accountId, expectedEpoch)
        loadStatusForAccount(accountId, target.sessionId, expectedEpoch)
        loadConversationsForAccount(accountId, expectedEpoch)
    }

    private suspend fun loadStatusForAccount(accountId: String, sessionId: String, expectedEpoch: Long) {
        runCatching { repo.status(sessionId, conversationClientId()) }.onSuccess { value ->
            if (_state.value.preferences.currentAccountId == accountId && accountEpoch == expectedEpoch) {
                applyAuthoritativeStatus(value, accountId, expectedEpoch)
            }
        }
    }

    private suspend fun applyAuthoritativeStatus(
        value: JsonElement,
        accountId: String,
        expectedEpoch: Long,
    ) {
        if (_state.value.preferences.currentAccountId != accountId || accountEpoch != expectedEpoch) return
        val resolvedSessionId = statusSessionId(value)
        val currentSessionId = _state.value.chatSessionId
        if (resolvedSessionId.isBlank() || resolvedSessionId == currentSessionId) {
            _state.update { it.copy(status = value, error = "") }
            return
        }
        val resolvedEntries = runCatching {
            parseHistoryMessages(
                repo.conversationMessages(resolvedSessionId),
                resolvedSessionId,
            )
        }.getOrNull().orEmpty()
        if (_state.value.preferences.currentAccountId != accountId || accountEpoch != expectedEpoch) return
        _state.update {
            it.copy(
                chatEntries = resolvedEntries.takeLast(100),
                chatSessionId = resolvedSessionId,
                chatClosed = false,
                status = value,
                error = "",
            )
        }
        persistChatNow(accountId)
    }

    private suspend fun loadConversationsForAccount(accountId: String, expectedEpoch: Long) {
        runCatching { repo.conversations() }.onSuccess { value ->
            if (_state.value.preferences.currentAccountId == accountId && accountEpoch == expectedEpoch) {
                _state.update { it.copy(conversations = value, error = "") }
            }
        }
    }

    private suspend fun loadAgentConfigForAccount(accountId: String, expectedEpoch: Long) {
        runCatching { repo.config() }.onSuccess { value ->
            if (_state.value.preferences.currentAccountId == accountId && accountEpoch == expectedEpoch) {
                _state.update { it.copy(agentConfig = value, modelCapabilities = null, error = "") }
            }
        }
    }

    fun checkForAppUpdate(forceRefresh: Boolean = false) {
        if (_appAbout.value.update is AppUpdateUiState.Checking ||
            _appAbout.value.update is AppUpdateUiState.Downloading) return
        _appAbout.update { it.copy(update = AppUpdateUiState.Checking) }
        viewModelScope.launch {
            val currentVersion = runCatching {
                getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0)
                    .versionName.orEmpty()
            }.getOrDefault("")
            val result = runCatching { appUpdateRepository.checkLatestRelease(currentVersion, forceRefresh) }
            _appAbout.update { current ->
                current.copy(
                    update = result.fold(
                        onSuccess = { checked ->
                            when (checked) {
                                is ReleaseCheckResult.UpdateAvailable -> AppUpdateUiState.Available(checked.release)
                                is ReleaseCheckResult.UpToDate -> AppUpdateUiState.UpToDate(checked.release)
                                is ReleaseCheckResult.ReleaseWithoutApk -> AppUpdateUiState.ReleaseWithoutApk(checked.release)
                                ReleaseCheckResult.NoPublishedRelease -> AppUpdateUiState.NoPublishedRelease
                            }
                        },
                        onFailure = { AppUpdateUiState.Failed },
                    ),
                )
            }
        }
    }

    fun selectAppUpdateDownloadSource(sourceId: String) {
        val supported = sourceId == AppUpdateRepository.AUTO_DOWNLOAD_SOURCE_ID ||
            AppUpdateRepository.DOWNLOAD_SOURCES.any { it.id == sourceId }
        if (!supported || _appAbout.value.update is AppUpdateUiState.Downloading) return
        _appAbout.update { it.copy(selectedDownloadSourceId = sourceId) }
    }

    fun downloadAppUpdate() {
        val release = when (val update = _appAbout.value.update) {
            is AppUpdateUiState.Available -> update.release
            is AppUpdateUiState.DownloadFailed -> update.release
            else -> return
        }
        val selectedSourceId = _appAbout.value.selectedDownloadSourceId
        _appAbout.update { it.copy(update = AppUpdateUiState.Downloading(release, 0)) }
        viewModelScope.launch {
            var lastSource: AppDownloadSource? = null
            var lastProgress = -1
            val result = runCatching {
                appUpdateRepository.downloadApk(release, selectedSourceId) { source, downloaded, total ->
                    val progress = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                    if (source.id != lastSource?.id || progress != lastProgress) {
                        lastSource = source
                        lastProgress = progress
                        _appAbout.update { current ->
                            if (current.update is AppUpdateUiState.Downloading) {
                                current.copy(update = AppUpdateUiState.Downloading(release, progress, source))
                            } else current
                        }
                    }
                }
            }
            result.onSuccess { downloaded ->
                _appAbout.update {
                    it.copy(update = AppUpdateUiState.Downloaded(release, downloaded.file.absolutePath, downloaded.source))
                }
                _messages.emit(UiMessage(getApplication<Application>().getString(R.string.feedback_update_downloaded), UiMessageType.Success))
            }.onFailure {
                _appAbout.update { it.copy(update = AppUpdateUiState.DownloadFailed(release, lastSource)) }
                _messages.emit(UiMessage(getApplication<Application>().getString(R.string.feedback_update_download_failed), UiMessageType.Error))
            }
        }
    }

    fun installDownloadedUpdate() {
        val update = _appAbout.value.update as? AppUpdateUiState.Downloaded ?: return
        val context = getApplication<Application>()
        val file = File(update.filePath)
        if (!file.isFile) {
            _appAbout.update { it.copy(update = AppUpdateUiState.DownloadFailed(update.release, update.source)) }
            emitMessage(R.string.feedback_update_download_failed, UiMessageType.Error)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            emitMessage(R.string.feedback_update_install_permission, UiMessageType.Info)
            return
        }
        runCatching {
            val uri = appUpdateRepository.contentUri(file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            emitMessage(R.string.feedback_update_install_failed, UiMessageType.Error)
        }
    }

    fun taskAction(id: String, action: String) = launchBusy("task:$id:$action", taskActionSuccessMessage(action)) { repo.taskAction(id, action); loadTasksInternal() }
    fun createCron(rawJson: String) = launchBusy("cron:new", R.string.feedback_cron_saved) { repo.createCron(rawJson); loadTasksInternal() }
    fun updateCron(id: String, rawJson: String) = launchBusy("cron:$id", R.string.feedback_cron_saved) { repo.updateCron(id, rawJson); loadTasksInternal() }
    fun deleteCron(id: String) = launchBusy("cron:$id", R.string.feedback_cron_deleted) { repo.deleteCron(id); loadTasksInternal() }
    fun setWhitelist(kind: String, scope: String, name: String, enabled: Boolean) = launchBusy("whitelist:$kind:$scope:$name", R.string.feedback_module_updated) { repo.setWhitelist(kind, scope, name, enabled); loadModulesInternal() }
    fun deleteFile(scope: String, path: String) = launchBusy("file:$scope:$path", R.string.feedback_deleted) {
        repo.deleteFile(scope, path)
        val listing = if (scope == "upload") _state.value.uploadFiles else _state.value.generatedFiles
        loadFileDirectory(scope, listing.fileListingPath(), listing.fileListingPage())
    }
    fun downloadFile(scope: String, path: String) = launchBusy("download:$scope:$path", R.string.feedback_downloaded) { repo.downloadFile(scope, path) }
    fun previewFile(scope: String, path: String, name: String) = launchBusy {
        val payload = repo.previewFile(scope, path, name)
        val extension = name.substringAfterLast('.', "").lowercase()
        val text = when {
            extension == "docx" -> extractDocxText(payload.bytes)
            extension in TEXT_PREVIEW_EXTENSIONS || payload.mimeType.startsWith("text/") -> payload.bytes.toString(Charsets.UTF_8)
            else -> ""
        }
        _state.update { it.copy(filePreview = FilePreviewUi(scope, path, payload.name, payload.mimeType, extension, payload.bytes, text)) }
    }
    fun clearFilePreview() = _state.update { it.copy(filePreview = null) }
    fun searchKnowledge(query: String) = viewModelScope.launch { loadValue({ repo.searchKnowledge(query) }) { value -> copy(knowledge = value) } }
    fun selectModel(model: String) = launchBusy("model", R.string.feedback_model_switched) { repo.setModel(model); loadModels() }
    fun patchAgentConfig(changes: JsonObject) = launchBusy("config", R.string.feedback_config_saved) { repo.patchConfig(changes); loadAgentConfig() }
    fun setReasoningEffort(effort: String) {
        val normalized = effort.trim()
        if (normalized.isBlank()) return
        launchBusy("reasoning-effort", R.string.feedback_reasoning_effort_updated) {
            repo.patchConfig(
                kotlinx.serialization.json.buildJsonObject {
                    put("provider", kotlinx.serialization.json.buildJsonObject {
                        put("reasoning_effort", JsonPrimitive(normalized))
                    })
                },
            )
            loadAgentConfigInternal()
            loadStatusInternal()
        }
    }

    fun deleteAccount(id: String) {
        viewModelScope.launch {
            val deletingCurrent = _state.value.preferences.currentAccountId == id
            repo.deleteAccount(id)
            if (deletingCurrent) {
                eventSocket?.stop()
                unlockManager.lock()
            }
        }
    }

    fun exportAccount(id: String, destination: Uri, password: String) {
        val passwordChars = password.toCharArray()
        launchBusy(
            key = ACCOUNT_TRANSFER_EXPORT_KEY,
            successMessage = R.string.feedback_account_exported,
            failureMessage = R.string.feedback_account_export_failed,
        ) {
            try {
                require(passwordChars.size >= AccountTransferCodec.MIN_PASSWORD_LENGTH)
                val encrypted = repo.exportAccount(id, passwordChars)
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(destination, "w")?.use { output ->
                        output.write(encrypted)
                        output.flush()
                    } ?: error("unable to open export destination")
                }
            } finally {
                passwordChars.fill('\u0000')
            }
        }
    }

    fun importAccount(source: Uri, password: String) {
        val passwordChars = password.toCharArray()
        launchBusy(
            key = ACCOUNT_TRANSFER_IMPORT_KEY,
            successMessage = R.string.feedback_account_imported,
            failureMessage = R.string.feedback_account_import_failed,
        ) {
            try {
                require(passwordChars.isNotEmpty())
                val encrypted = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openInputStream(source)?.use(::readAccountTransferBytes)
                        ?: error("unable to open account file")
                }
                val imported = repo.importAccount(encrypted, passwordChars)
                if (prefs.snapshot().currentAccountId == imported.id) {
                    eventSocket?.stop()
                    startSocket()
                }
            } finally {
                passwordChars.fill('\u0000')
            }
        }
    }

    fun switchAccount(id: String): Boolean {
        val snapshot = _state.value
        if (id.isBlank() || snapshot.preferences.currentAccountId == id) return false
        if (snapshot.streaming || snapshot.busy || snapshot.chatAttachmentUploading || snapshot.guidanceSubmitting) {
            emitMessage(R.string.feedback_account_switch_busy, UiMessageType.Info)
            return false
        }
        val outgoingAccountId = snapshot.preferences.currentAccountId
        val outgoingHistory = ApiClient.json.encodeToString(snapshot.chatEntries.takeLast(100))
        val outgoingSessionId = snapshot.chatSessionId
        persistChatJob?.cancel()
        accountRestoreJob?.cancel()
        pendingNextTurnGuidance = null
        accountEpoch += 1L
        _state.update { current ->
            current.copy(
                configured = false,
                chatEntries = emptyList(),
                chatSessionId = "",
                chatClosed = false,
                streaming = false,
                conversations = null,
                tasks = null,
                cron = null,
                status = null,
                expands = null,
                senses = null,
                uploadFiles = null,
                generatedFiles = null,
                knowledge = null,
                models = null,
                modelCapabilities = null,
                agentConfig = null,
                avatarBytes = null,
                versions = null,
                filePreview = null,
                pendingChatAttachments = emptyList(),
                activeRunId = "",
                chatStopping = false,
                error = "",
            )
        }
        viewModelScope.launch {
            if (outgoingAccountId.isNotBlank() && outgoingSessionId.isNotBlank()) {
                prefs.saveChatState(outgoingAccountId, outgoingHistory, outgoingSessionId)
            }
            eventSocket?.stop()
            runCatching { repo.ensureAccountSession(id) }
                .onSuccess {
                    prefs.setCurrentAccount(id)
                    if (repo.appPasswordConfigured(id)) {
                        unlockManager.lock()
                    } else {
                        unlockManager.unlock()
                        startSocket()
                    }
                }
                .onFailure {
                    val message = getApplication<Application>().getString(R.string.feedback_account_reconnect_required)
                    // The target was never published as current, so restore the full
                    // outgoing account UI instead of leaving a blank/disconnected chat.
                    _state.value = snapshot.copy(error = message)
                    _messages.emit(UiMessage(message, UiMessageType.Error))
                    unlockManager.unlock()
                    startSocket()
                }
        }
        return true
    }

    fun logout() {
        viewModelScope.launch {
            currentAccount()?.let { repo.clearSession(it.id) }
            eventSocket?.stop()
            unlockManager.lock()
        }
    }

    fun setTheme(value: String) = viewModelScope.launch { prefs.setThemeMode(value) }
    fun setTone(value: String) = viewModelScope.launch { prefs.setToneAndDisableDynamicColor(value) }
    fun setLanguage(value: String) = viewModelScope.launch { prefs.setLanguage(value) }
    fun setNotifications(value: Boolean) = viewModelScope.launch { prefs.setNotifications(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { prefs.setDynamicColor(value) }
    fun setThemeBackground(uri: String, mimeType: String) {
        val previous = _state.value.preferences.themeBackgroundUri
        _state.update { current ->
            current.copy(
                preferences = current.preferences.copy(themeBackgroundUri = uri, themeBackgroundMime = mimeType),
                themeBackgroundRevision = current.themeBackgroundRevision + 1L,
            )
        }
        viewModelScope.launch {
            if (previous.isNotBlank() && previous != uri) releaseThemeBackgroundPermission(previous)
            prefs.setThemeBackground(uri, mimeType)
            _messages.emit(UiMessage(getApplication<Application>().getString(R.string.feedback_background_updated), UiMessageType.Success))
        }
    }

    fun renameAccount(id: String, displayName: String) {
        val normalized = displayName.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            prefs.renameAccount(id, normalized)
            emitMessage(R.string.feedback_account_renamed, UiMessageType.Success)
        }
    }

    fun resetTheme() {
        val previous = _state.value.preferences.themeBackgroundUri
        _state.update { current ->
            current.copy(
                preferences = current.preferences.copy(
                    themeMode = "system",
                    tone = "Purple",
                    dynamicColor = false,
                    themeBackgroundUri = "",
                    themeBackgroundMime = "",
                ),
                themeBackgroundRevision = current.themeBackgroundRevision + 1L,
            )
        }
        viewModelScope.launch {
            releaseThemeBackgroundPermission(previous)
            prefs.resetTheme()
            _messages.emit(UiMessage(getApplication<Application>().getString(R.string.feedback_theme_restored), UiMessageType.Success))
        }
    }
    fun setDownloadDirectoryUri(value: String) = viewModelScope.launch { prefs.setDownloadDirectoryUri(value) }
    fun setBiometricEnabled(value: Boolean) = viewModelScope.launch { prefs.setBiometricEnabled(value) }

    private fun releaseThemeBackgroundPermission(uri: String) {
        if (uri.isBlank()) return
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    fun changeAppPassword(oldPassword: String, newPassword: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val account = currentAccount()
            val changed = runCatching {
                account != null && newPassword.length >= 4 && repo.changeAppPassword(account.id, oldPassword, newPassword)
            }.getOrDefault(false)
            _messages.emit(
                UiMessage(
                    getApplication<Application>().getString(
                        if (changed) R.string.feedback_password_updated else R.string.feedback_password_failed,
                    ),
                    if (changed) UiMessageType.Success else UiMessageType.Error,
                ),
            )
            onResult(changed)
        }
    }

    fun reportBiometricRequired() = emitMessage(R.string.feedback_biometric_required, UiMessageType.Info)
    fun reportBiometricFailed() {
        val text = getApplication<Application>().getString(R.string.feedback_biometric_failed)
        _state.update { it.copy(error = text) }
        emitMessage(R.string.feedback_biometric_failed, UiMessageType.Error)
    }
    fun reportPasswordFailed() = emitMessage(R.string.feedback_password_failed, UiMessageType.Error)
    fun clearError() = _state.update { it.copy(error = "") }

    private suspend fun loadTasksInternal() {
        val requestContext = accountRequestContext()
        runCatching {
            val plans = repo.taskPlans()
            val crons = repo.cron()
            repo.updateWidgetSummary()
            if (isCurrentAccountRequest(requestContext)) {
                _state.update { it.copy(tasks = plans, cron = crons, error = "") }
            }
        }.onFailure {
            if (isCurrentAccountRequest(requestContext)) {
                _state.update { state -> state.copy(error = getApplication<Application>().getString(R.string.error_generic)) }
            }
        }
    }

    private suspend fun loadStatusInternal() {
        val requestContext = accountRequestContext()
        val sessionId = _state.value.chatSessionId
        runCatching { repo.status(sessionId, conversationClientId()) }
            .onSuccess { value ->
                if (isCurrentAccountRequest(requestContext)) {
                    applyAuthoritativeStatus(value, requestContext.accountId, requestContext.epoch)
                }
            }
            .onFailure {
                if (isCurrentAccountRequest(requestContext)) {
                    _state.update { state ->
                        state.copy(error = getApplication<Application>().getString(R.string.error_generic))
                    }
                }
            }
    }
    private suspend fun loadAgentConfigInternal() = loadValue({ repo.config() }) { value -> copy(agentConfig = value) }
    private suspend fun loadConversationsInternal() = loadValue({ repo.conversations() }) { value -> copy(conversations = value) }
    private suspend fun loadModulesInternal() {
        val requestContext = accountRequestContext()
        runCatching { repo.expandsData() to repo.senses() }
            .onSuccess { values ->
                if (isCurrentAccountRequest(requestContext)) {
                    _state.update { it.copy(expands = values.first, senses = values.second, error = "") }
                }
            }
            .onFailure {
                if (isCurrentAccountRequest(requestContext)) {
                    _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) }
                }
            }
    }

    private suspend fun loadFileDirectory(scope: String, path: String, page: Int = 1) {
        val requestContext = accountRequestContext()
        val normalizedScope = scope.lowercase()
        require(normalizedScope == "upload" || normalizedScope == "download") { "unsupported file scope" }
        runCatching { repo.files(normalizedScope, path, page) }
            .onSuccess { value ->
                if (isCurrentAccountRequest(requestContext)) {
                    _state.update {
                        if (normalizedScope == "upload") it.copy(uploadFiles = value, error = "")
                        else it.copy(generatedFiles = value, error = "")
                    }
                }
            }
            .onFailure {
                if (isCurrentAccountRequest(requestContext)) {
                    _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) }
                }
            }
    }

    private fun scheduleChatPersist() {
        persistChatJob?.cancel()
        persistChatJob = viewModelScope.launch {
            delay(180)
            persistChatNow()
        }
    }

    private suspend fun persistChatNow(accountId: String = _state.value.preferences.currentAccountId) {
        val snapshot = _state.value
        if (accountId.isBlank() || snapshot.chatSessionId.isBlank()) return
        prefs.saveChatState(
            accountId,
            ApiClient.json.encodeToString(snapshot.chatEntries.takeLast(100)),
            snapshot.chatSessionId,
        )
    }

    private suspend fun loadValue(block: suspend () -> JsonElement, reducer: AppUiState.(JsonElement) -> AppUiState) {
        val requestContext = accountRequestContext()
        runCatching { block() }
            .onSuccess { value ->
                if (isCurrentAccountRequest(requestContext)) {
                    _state.update { it.reducer(value).copy(error = "") }
                }
            }
            .onFailure {
                if (isCurrentAccountRequest(requestContext)) {
                    _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) }
                }
            }
    }

    private fun accountRequestContext(): AccountRequestContext = AccountRequestContext(
        accountId = _state.value.preferences.currentAccountId,
        epoch = accountEpoch,
    )

    private fun isCurrentAccountRequest(value: AccountRequestContext): Boolean =
        value.accountId == _state.value.preferences.currentAccountId && value.epoch == accountEpoch

    private fun launchBusy(block: suspend () -> Unit) =
        launchBusyInternal(
            key = null,
            successMessage = null,
            failureMessage = R.string.error_generic,
            block = block,
        )

    private fun launchBusy(
        key: String,
        successMessage: Int? = null,
        failureMessage: Int = R.string.error_generic,
        block: suspend () -> Unit,
    ) = launchBusyInternal(
        key = key,
        successMessage = successMessage,
        failureMessage = failureMessage,
        block = block,
    )

    private fun launchBusyInternal(
        key: String?,
        successMessage: Int?,
        failureMessage: Int,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = "") }
            key?.let { pendingKey -> _pendingKeys.update { it + pendingKey } }
            runCatching { block() }
                .onSuccess {
                    successMessage?.let { message ->
                        _messages.emit(UiMessage(getApplication<Application>().getString(message), UiMessageType.Success))
                    }
                }
                .onFailure {
                    val errorText = getApplication<Application>().getString(failureMessage)
                    _state.update { it.copy(error = errorText) }
                    _messages.emit(UiMessage(errorText, UiMessageType.Error))
                }
            key?.let { pendingKey -> _pendingKeys.update { it - pendingKey } }
            _state.update { it.copy(busy = false) }
        }
    }

    private fun taskActionSuccessMessage(action: String): Int? = when (action.lowercase()) {
        "approve" -> R.string.feedback_task_approved
        "pause" -> R.string.feedback_task_paused
        "resume" -> R.string.feedback_task_resumed
        "abort" -> R.string.feedback_task_aborted
        else -> null
    }

    private fun emitMessage(messageRes: Int, type: UiMessageType) {
        viewModelScope.launch {
            _messages.emit(UiMessage(getApplication<Application>().getString(messageRes), type))
        }
    }

    private fun readAccountTransferBytes(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= AccountTransferCodec.MAX_FILE_BYTES) { "account file is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private suspend fun currentAccount(): AccountConfig? = prefs.snapshot().let { values -> values.accounts.firstOrNull { it.id == values.currentAccountId } }

    private suspend fun startSocket() {
        val account = currentAccount() ?: return
        val secrets = repo.secrets(account.id)
        if (secrets.deviceToken.isBlank() || secrets.sessionToken.isBlank()) return
        val bundle = repo.bundle().second
        eventSocket?.stop()
        val socket = EventSocket(
            scope = viewModelScope,
            client = bundle.client,
            url = account.baseUrl,
            deviceToken = secrets.deviceToken,
            sessionToken = secrets.sessionToken,
            deviceId = deviceId,
            onEvent = { event ->
                if (_state.value.preferences.currentAccountId == account.id) onEvent(event)
            },
            onOpen = { DeviceActionReporter.flush(getApplication(), account.username, deviceId) },
            onAuthenticationFailed = {
                if (_state.value.preferences.currentAccountId == account.id) {
                    viewModelScope.launch {
                        repo.clearSession(account.id)
                        _state.update { it.copy(configured = false) }
                        scheduleAccountReconnect(account.id, accountEpoch, reportFailure = true)
                    }
                }
            },
        )
        eventSocket = socket
        DeviceActionReporter.attach(account.username, deviceId) { commandId, status, detail ->
            socket.sendDeviceResult(commandId, status, detail)
        }
        socket.start()
    }

    private fun onEvent(event: EventDto) {
        if (event.type == "device.command") {
            val command = runCatching {
                ApiClient.json.decodeFromJsonElement(DeviceActionCommand.serializer(), event.data ?: return)
            }.getOrNull() ?: return
            val result = DeviceActionCoordinator.accept(getApplication(), command)
            DeviceActionReporter.report(getApplication(), command, result)
            return
        }
        if (!_state.value.preferences.notifications || event.type == "connected") return
        val context = getApplication<Application>()
        val data = event.data as? JsonObject ?: JsonObject(emptyMap())
        val itemTitle = data.notificationText("title", "name", "task_id", "plan_id")
            .ifBlank { context.getString(R.string.app_name) }
        when {
            event.type == "conversation.completed" -> {
                val sessionId = data.notificationText("session_id")
                if (!sessionId.startsWith("app-")) return
                postNotification(
                    key = "conversation:$sessionId",
                    channel = KemoApp.CHAT_CHANNEL,
                    title = context.getString(R.string.notification_chat_complete_title),
                    text = context.getString(R.string.notification_chat_complete_body, data.notificationText("title").ifBlank { sessionId }),
                    openTasks = false,
                )
            }
            event.type.startsWith("cron") -> {
                val state = data.notificationText("last_state", "status").lowercase()
                postNotification(
                    key = "cron:${data.notificationText("task_id", "id")}:${data.notificationText("latest_run_at", "updated_at")}",
                    channel = KemoApp.TASK_CHANNEL,
                    title = context.getString(if (state == "failed") R.string.notification_cron_failed_title else R.string.notification_cron_complete_title),
                    text = "$itemTitle · ${context.getString(R.string.notification_open_app)}",
                    openTasks = true,
                )
            }
            event.type.startsWith("task_plan") -> {
                val eventSession = data.notificationText("session_id")
                if (eventSession.isBlank() || eventSession != _state.value.chatSessionId) return
                val titleRes = when {
                    event.type.endsWith("failed") -> R.string.notification_task_failed_title
                    event.type.endsWith("awaiting_approval") -> R.string.notification_task_approval_title
                    else -> R.string.notification_task_complete_title
                }
                postNotification(
                    key = "${event.type}:${data.notificationText("plan_id", "id")}:${data.notificationText("updated_at")}",
                    channel = KemoApp.TASK_CHANNEL,
                    title = context.getString(titleRes),
                    text = "$itemTitle · ${context.getString(R.string.notification_open_app)}",
                    openTasks = true,
                )
            }
            event.type == "system.warning" -> postNotification(
                key = "system:${event.ts}",
                channel = KemoApp.SYSTEM_CHANNEL,
                title = context.getString(R.string.notification_system),
                text = context.getString(R.string.notification_default),
                openTasks = false,
            )
        }
    }

    private fun JsonObject.notificationText(vararg keys: String): String {
        keys.forEach { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)?.let { return it }
        }
        return ""
    }

    private fun postNotification(key: String, channel: String, title: String, text: String, openTasks: Boolean) {
        val context = getApplication<Application>()
        if (!context.getSharedPreferences("kemo_notification_state", Context.MODE_PRIVATE)
                .getBoolean("enabled", true) || !_state.value.preferences.notifications) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val now = System.currentTimeMillis()
        synchronized(recentNotifications) {
            recentNotifications.entries.removeAll { now - it.value > 60_000L }
            if (now - (recentNotifications[key] ?: 0L) < 30_000L) return
            recentNotifications[key] = now
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (openTasks) data = android.net.Uri.parse("kemo://task/${UUID.randomUUID()}")
        }
        val requestCode = key.hashCode()
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(requestCode, notification)
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        DeviceActionReporter.detach()
        eventSocket?.stop()
        super.onCleared()
    }

    private fun parseHistoryMessages(payload: JsonElement, sessionId: String): List<ChatEntry> {
        val root = payload as? JsonObject
        val messages = when (payload) {
            is JsonArray -> payload
            is JsonObject -> payload["messages"] as? JsonArray ?: payload["items"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        fun JsonObject.number(key: String): Long = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
        fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        fun contentText(value: JsonElement?): String = when (value) {
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            is JsonArray -> value.joinToString("") { block ->
                val item = block as? JsonObject
                item?.string("text").orEmpty().ifBlank { item?.string("content").orEmpty() }
            }
            else -> ""
        }
        fun attachment(value: JsonElement): ChatAttachmentUi? {
            val item = value as? JsonObject ?: return null
            val path = item.string("relative_path").ifBlank { item.string("path") }
            val name = item.string("name").ifBlank { path.substringAfterLast('/').substringAfterLast('\\') }
            if (path.isBlank() || name.isBlank()) return null
            return ChatAttachmentUi(
                name = name,
                path = path,
                mimeType = item.string("mime_type").ifBlank { "application/octet-stream" },
                mediaKind = item.string("media_kind").ifBlank { "file" },
                size = item.number("size"),
            )
        }
        fun artifacts(value: JsonElement?): List<ChatMediaUi> = (value as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val assetId = item.string("asset_id")
            val path = item.string("path")
            val name = item.string("name").ifBlank { path.substringAfterLast('/').substringAfterLast('\\') }
            if (assetId.isBlank() || path.isBlank() || name.isBlank()) return@mapNotNull null
            ChatMediaUi(
                assetId = assetId,
                type = item.string("type").ifBlank { "file" },
                name = name,
                path = path,
                mimeType = item.string("mime_type").ifBlank { "application/octet-stream" },
                size = item.number("size"),
                checksumSha256 = item.string("checksum_sha256"),
                durationMs = item.number("duration_ms"),
            )
        }
        val metrics = (root?.get("round_metrics") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .associateBy { it.number("round").toInt() }
        val traces = (root?.get("round_traces") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .associateBy { it.number("round").toInt() }
        var round = ((root?.get("pagination") as? JsonObject)?.number("first_round") ?: 1L).toInt().coerceAtLeast(1) - 1
        return buildList {
            messages.forEachIndexed { index, element ->
                val item = element as? JsonObject ?: return@forEachIndexed
                val role = item.string("role").lowercase()
                val text = contentText(item["content"] ?: item["text"] ?: item["message"])
                if (role == "user") {
                    round += 1
                    val inputAttachments = (item["attachments"] as? JsonArray).orEmpty().mapNotNull(::attachment)
                    if (text.isNotBlank() || inputAttachments.isNotEmpty()) {
                        add(ChatEntry("history-$sessionId-$index", ChatRole.USER, text = text, attachments = inputAttachments))
                    }
                    return@forEachIndexed
                }
                if (role != "assistant") return@forEachIndexed
                val trace = traces[round]
                val tools = (trace?.get("tools") as? JsonArray).orEmpty().mapNotNull { toolValue ->
                    val tool = toolValue as? JsonObject ?: return@mapNotNull null
                    val callId = tool.string("call_id").ifBlank { "history-tool-$round-${tool.hashCode()}" }
                    val status = when (tool.string("status").lowercase()) {
                        "running" -> ToolStatus.RUNNING
                        "completed", "success", "duplicate_reused" -> ToolStatus.SUCCESS
                        else -> ToolStatus.FAILED
                    }
                    com.kesepain.kemoapp.data.stream.ToolCallUi(
                        callId = callId,
                        name = tool.string("name"),
                        arguments = tool.string("arguments_text"),
                        status = status,
                        resultPreview = tool.string("result_text"),
                        elapsedMs = tool.number("elapsed_ms"),
                    )
                }
                val metric = metrics[round]
                val usageObject = metric?.get("usage") as? JsonObject
                val promptTokens = usageObject?.number("prompt_tokens") ?: 0L
                val totalTokens = usageObject?.number("total_tokens")?.takeIf { it > 0 }
                    ?: promptTokens + (usageObject?.number("completion_tokens") ?: 0L)
                val cachedTokens = usageObject?.number("cached_prompt_tokens") ?: 0L
                val usage = metric?.let {
                    ChatUsageUi(
                        totalTokens = totalTokens,
                        cacheHitRate = if (promptTokens > 0) cachedTokens.toDouble() / promptTokens else 0.0,
                        elapsedMs = it.number("elapsed_ms"),
                    )
                }
                val media = (
                    artifacts(metric?.get("artifacts")) +
                        (trace?.get("tools") as? JsonArray).orEmpty().flatMap { tool -> artifacts((tool as? JsonObject)?.get("artifacts")) }
                    ).distinctBy { "${it.assetId}:${it.path}" }
                if (text.isNotBlank() || trace?.string("reasoning").orEmpty().isNotBlank() || tools.isNotEmpty() || media.isNotEmpty()) {
                    add(
                        ChatEntry(
                            id = "history-$sessionId-$index",
                            role = ChatRole.ASSISTANT,
                            text = text,
                            reasoning = trace?.string("reasoning").orEmpty(),
                            tools = tools,
                            usage = usage,
                            media = media,
                        ),
                    )
                }
                val details = metric?.get("guidance_details") as? JsonArray
                if (!details.isNullOrEmpty()) {
                    details.forEachIndexed { guidanceIndex, detailValue ->
                        val detail = detailValue as? JsonObject ?: return@forEachIndexed
                        val guidanceAttachments = (detail["uploaded_files"] as? JsonArray).orEmpty().mapNotNull(::attachment)
                        add(
                            ChatEntry(
                                id = detail.string("id").ifBlank { "history-guidance-$round-$guidanceIndex" },
                                role = ChatRole.GUIDANCE,
                                text = detail.string("display_text").ifBlank { detail.string("text") },
                                attachments = guidanceAttachments,
                                guidanceStatus = GuidanceStatus.COMPLETED,
                            ),
                        )
                    }
                } else {
                    (metric?.get("guidance") as? JsonArray).orEmpty().forEachIndexed { guidanceIndex, guidance ->
                        val value = (guidance as? JsonPrimitive)?.contentOrNull.orEmpty()
                        if (value.isNotBlank()) add(ChatEntry("history-guidance-$round-$guidanceIndex", ChatRole.GUIDANCE, text = value, guidanceStatus = GuidanceStatus.COMPLETED))
                    }
                }
            }
        }
    }

    private fun extractDocxText(bytes: ByteArray): String = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return@use xml
                        .replace(Regex("</w:p>"), "\n")
                        .replace(Regex("<w:tab[^>]*/>"), "\t")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace(Regex("\n{3,}"), "\n\n")
                        .trim()
                }
            }
            ""
        }
    }.getOrDefault("")

    companion object {
        const val ACCOUNT_TRANSFER_IMPORT_KEY = "account-transfer:import"
        const val ACCOUNT_TRANSFER_EXPORT_KEY = "account-transfer:export"
        private const val FOREGROUND_RECONNECT_COOLDOWN_MS = 5_000L
        private const val STREAM_RENDER_INTERVAL_MS = 64L
        private val TEXT_PREVIEW_EXTENSIONS = setOf(
            "txt", "md", "markdown", "json", "xml", "csv", "tsv", "log", "ini", "conf", "yaml", "yml",
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "sql", "sh", "ps1",
        )
    }
}

