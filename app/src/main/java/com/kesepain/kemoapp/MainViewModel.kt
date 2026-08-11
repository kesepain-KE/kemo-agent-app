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
import androidx.lifecycle.viewModelScope
import com.kesepain.kemoapp.data.local.AccountConfig
import com.kesepain.kemoapp.data.local.AppPreferences
import com.kesepain.kemoapp.data.local.Prefs
import com.kesepain.kemoapp.data.local.SecureStore
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
import com.kesepain.kemoapp.security.UnlockManager
import com.kesepain.kemoapp.update.AppAboutUiState
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID
import java.io.ByteArrayInputStream
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
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
    private var chatRestored = false
    private var chatJob: Job? = null
    private var persistChatJob: Job? = null
    private var pendingNextTurnGuidance: PendingNextTurnGuidance? = null
    private val recentNotifications = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            prefs.flow.collectLatest { values ->
                val account = values.accounts.firstOrNull { it.id == values.currentAccountId }
                val credentials = account?.let { repo.credentialState(it.id) }
                val configured = credentials?.sessionToken?.isNotBlank() == true && credentials.deviceToken.isNotBlank()
                val hasSavedAccounts = values.accounts.isNotEmpty()
                _state.update { current ->
                    if (!chatRestored) {
                        chatRestored = true
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
                if (values.chatSessionId.isBlank() && _state.value.chatSessionId.isNotBlank()) persistChatNow()
                if (account != null && secure.get(account.id, SecureStore.APP_PASSWORD_HASH).isNotBlank()) {
                    // Security is configured; remain locked until explicit authentication.
                } else if (hasSavedAccounts) {
                    unlockManager.unlock()
                }
            }
        }
        viewModelScope.launch { unlockManager.unlocked.collectLatest { unlocked -> _state.update { it.copy(unlocked = unlocked) }; if (unlocked) startSocket() } }
        repo.onSessionExpired = { viewModelScope.launch {
            currentAccount()?.let { repo.clearSession(it.id) }
            eventSocket?.stop()
            _state.update { it.copy(configured = false, error = "会话已过期，请重新连接") }
        } }
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
        if ((prompt.isBlank() && pendingAttachments.isEmpty()) || snapshot.chatAttachmentUploading) return
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
        chatJob = viewModelScope.launch {
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
                        repo.streamChat(prompt, sessionId, assistantId, pendingAttachments.map { it.path }, reasoningEffort) { event ->
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
                    },
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
            chatJob = null
            if (queued != null) {
                startChat(
                    prompt = queued.text,
                    reasoningEffort = queued.reasoningEffort,
                    pendingAttachments = queued.attachments,
                    reuseUserEntryId = queued.entryId,
                )
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

    fun newConversation() {
        if (_state.value.streaming) return
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
                // display the previous session after "clear conversation".
                status = null,
                error = "",
            )
        }
        viewModelScope.launch { persistChatNow() }
        // Ask the bridge for the fresh session snapshot immediately. This keeps
        // the panel populated with zero/initial values without waiting for the
        // next outbound chat request to trigger a status refresh.
        viewModelScope.launch { loadStatusInternal() }
    }

    fun clearConversation() = newConversation()

    fun saveConversation() = launchBusy {
        val sessionId = _state.value.chatSessionId
        require(sessionId.isNotBlank() && _state.value.chatEntries.isNotEmpty()) { "当前没有可保存的对话" }
        repo.closeConversation(sessionId)
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
        if (sessionId.isNotBlank() && _state.value.chatEntries.isNotEmpty()) repo.closeConversation(sessionId)
        _state.update { it.copy(chatEntries = emptyList(), chatSessionId = "app-${UUID.randomUUID()}", chatClosed = false, status = null) }
        persistChatNow()
        loadStatusInternal()
        loadConversationsInternal()
    }

    fun switchConversation(sessionId: String) = launchBusy {
        require(sessionId.isNotBlank()) { "会话标识为空" }
        val payload = repo.conversationMessages(sessionId)
        val entries = parseHistoryMessages(payload)
        _state.update { it.copy(chatEntries = entries.takeLast(100), chatSessionId = sessionId, chatClosed = true, status = null, error = "") }
        loadStatusInternal()
        persistChatNow()
    }

    fun deleteConversation(sessionId: String) = launchBusy {
        repo.deleteConversation(sessionId)
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
    fun loadModels() = launchBusy("refresh:models") { loadValue({ repo.models() }) { value -> copy(models = value) } }
    fun loadAgentConfig() = launchBusy("refresh:config") { loadValue({ repo.config() }) { value -> copy(agentConfig = value) } }
    fun loadProfileData() = viewModelScope.launch {
        runCatching { repo.avatar() to repo.version() }
            .onSuccess { values -> _state.update { it.copy(avatarBytes = values.first, versions = values.second, error = "") } }
            .onFailure { failure -> _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) } }
    }

    fun loadAppAbout() {
        if (_appAbout.value.avatarBytes != null || _appAbout.value.avatarLoading) return
        _appAbout.update { it.copy(avatarLoading = true) }
        viewModelScope.launch {
            val avatar = runCatching { appUpdateRepository.loadGitHubAvatar() }.getOrNull()
            _appAbout.update { it.copy(avatarBytes = avatar, avatarLoading = false) }
        }
    }

    fun checkForAppUpdate() {
        if (_appAbout.value.update is AppUpdateUiState.Checking ||
            _appAbout.value.update is AppUpdateUiState.Downloading) return
        _appAbout.update { it.copy(update = AppUpdateUiState.Checking) }
        viewModelScope.launch {
            val currentVersion = runCatching {
                getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0)
                    .versionName.orEmpty()
            }.getOrDefault("")
            val result = runCatching { appUpdateRepository.checkLatestRelease(currentVersion) }
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

    fun downloadAppUpdate() {
        val release = when (val update = _appAbout.value.update) {
            is AppUpdateUiState.Available -> update.release
            is AppUpdateUiState.DownloadFailed -> update.release
            else -> return
        }
        _appAbout.update { it.copy(update = AppUpdateUiState.Downloading(release, 0)) }
        viewModelScope.launch {
            val result = runCatching {
                appUpdateRepository.downloadApk(release) { downloaded, total ->
                    val progress = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                    _appAbout.update { current ->
                        if (current.update is AppUpdateUiState.Downloading) {
                            current.copy(update = AppUpdateUiState.Downloading(release, progress))
                        } else current
                    }
                }
            }
            result.onSuccess { file ->
                _appAbout.update { it.copy(update = AppUpdateUiState.Downloaded(release, file.absolutePath)) }
                _messages.emit(UiMessage(getApplication<Application>().getString(R.string.feedback_update_downloaded), UiMessageType.Success))
            }.onFailure {
                _appAbout.update { it.copy(update = AppUpdateUiState.DownloadFailed(release)) }
                _messages.emit(UiMessage(getApplication<Application>().getString(R.string.feedback_update_download_failed), UiMessageType.Error))
            }
        }
    }

    fun installDownloadedUpdate() {
        val update = _appAbout.value.update as? AppUpdateUiState.Downloaded ?: return
        val context = getApplication<Application>()
        val file = File(update.filePath)
        if (!file.isFile) {
            _appAbout.update { it.copy(update = AppUpdateUiState.DownloadFailed(update.release)) }
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

    fun switchAccount(id: String) {
        viewModelScope.launch {
            prefs.setCurrentAccount(id)
            eventSocket?.stop()
            unlockManager.lock()
        }
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
        runCatching {
            val plans = repo.taskPlans()
            val crons = repo.cron()
            repo.updateWidgetSummary()
            _state.update { it.copy(tasks = plans, cron = crons, error = "") }
        }.onFailure { _state.update { state -> state.copy(error = getApplication<Application>().getString(R.string.error_generic)) } }
    }

    private suspend fun loadStatusInternal() = loadValue({ repo.status(_state.value.chatSessionId) }) { value -> copy(status = value) }
    private suspend fun loadConversationsInternal() = loadValue({ repo.conversations() }) { value -> copy(conversations = value) }
    private suspend fun loadModulesInternal() {
        runCatching { repo.expandsData() to repo.senses() }
            .onSuccess { values -> _state.update { it.copy(expands = values.first, senses = values.second, error = "") } }
            .onFailure { failure -> _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) } }
    }

    private suspend fun loadFileDirectory(scope: String, path: String, page: Int = 1) {
        val normalizedScope = scope.lowercase()
        require(normalizedScope == "upload" || normalizedScope == "download") { "unsupported file scope" }
        runCatching { repo.files(normalizedScope, path, page) }
            .onSuccess { value ->
                _state.update {
                    if (normalizedScope == "upload") it.copy(uploadFiles = value, error = "")
                    else it.copy(generatedFiles = value, error = "")
                }
            }
            .onFailure { failure -> _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) } }
    }

    private fun scheduleChatPersist() {
        persistChatJob?.cancel()
        persistChatJob = viewModelScope.launch {
            delay(180)
            persistChatNow()
        }
    }

    private suspend fun persistChatNow() {
        val snapshot = _state.value
        if (snapshot.chatSessionId.isBlank()) return
        prefs.saveChatState(ApiClient.json.encodeToString(snapshot.chatEntries.takeLast(100)), snapshot.chatSessionId)
    }

    private suspend fun loadValue(block: suspend () -> JsonElement, reducer: AppUiState.(JsonElement) -> AppUiState) {
        runCatching { block() }
            .onSuccess { value -> _state.update { it.reducer(value).copy(error = "") } }
            .onFailure { failure -> _state.update { it.copy(error = getApplication<Application>().getString(R.string.error_generic)) } }
    }

    private fun launchBusy(block: suspend () -> Unit) = launchBusyInternal(key = null, successMessage = null, block = block)

    private fun launchBusy(key: String, successMessage: Int? = null, block: suspend () -> Unit) =
        launchBusyInternal(key = key, successMessage = successMessage, block = block)

    private fun launchBusyInternal(key: String?, successMessage: Int?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = "") }
            key?.let { pendingKey -> _pendingKeys.update { it + pendingKey } }
            runCatching { block() }
                .onSuccess {
                    successMessage?.let { message ->
                        _messages.emit(UiMessage(getApplication<Application>().getString(message), UiMessageType.Success))
                    }
                }
                .onFailure { failure ->
                    val errorText = getApplication<Application>().getString(R.string.error_generic)
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

    private suspend fun currentAccount(): AccountConfig? = prefs.snapshot().let { values -> values.accounts.firstOrNull { it.id == values.currentAccountId } }

    private suspend fun startSocket() {
        val account = currentAccount() ?: return
        val secrets = repo.secrets(account.id)
        if (secrets.deviceToken.isBlank() || secrets.sessionToken.isBlank()) return
        val bundle = repo.bundle().second
        eventSocket?.stop()
        eventSocket = EventSocket(
            scope = viewModelScope,
            client = bundle.client,
            url = account.baseUrl,
            deviceToken = secrets.deviceToken,
            sessionToken = secrets.sessionToken,
            deviceId = deviceId,
            onEvent = ::onEvent,
        ).also { it.start() }
    }

    private fun onEvent(event: EventDto) {
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

    override fun onCleared() { eventSocket?.stop(); super.onCleared() }

    private fun parseHistoryMessages(payload: JsonElement): List<ChatEntry> {
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
                        add(ChatEntry("history-${_state.value.chatSessionId}-$index", ChatRole.USER, text = text, attachments = inputAttachments))
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
                            id = "history-${_state.value.chatSessionId}-$index",
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
        private const val STREAM_RENDER_INTERVAL_MS = 64L
        private val TEXT_PREVIEW_EXTENSIONS = setOf(
            "txt", "md", "markdown", "json", "xml", "csv", "tsv", "log", "ini", "conf", "yaml", "yml",
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "sql", "sh", "ps1",
        )
    }
}

