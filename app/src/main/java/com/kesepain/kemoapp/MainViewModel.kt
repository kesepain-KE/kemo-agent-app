package com.kesepain.kemoapp

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.kesepain.kemoapp.data.stream.ChatRole
import com.kesepain.kemoapp.data.stream.ChatUsageUi
import com.kesepain.kemoapp.data.stream.StreamEvent
import com.kesepain.kemoapp.data.stream.ToolStatus
import com.kesepain.kemoapp.security.UnlockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID
import java.io.ByteArrayInputStream
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

data class AppUiState(
    val preferences: AppPreferences = AppPreferences(),
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
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Prefs(application)
    private val secure = SecureStore(application)
    private val repo = KemoRepository(application)
    private val deviceId = appDeviceId(application)
    private val _state = MutableStateFlow(AppUiState())
    private val unlockManager = UnlockManager { _state.value.preferences.autoLockMinutes }
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var eventSocket: EventSocket? = null
    private var chatRestored = false
    private var persistChatJob: Job? = null

    init {
        viewModelScope.launch {
            prefs.flow.collectLatest { values ->
                val account = values.accounts.firstOrNull { it.id == values.currentAccountId }
                val credentials = account?.let { repo.credentialState(it.id) }
                val configured = credentials?.sessionToken?.isNotBlank() == true && credentials.deviceToken.isNotBlank()
                _state.update { current ->
                    if (!chatRestored) {
                        chatRestored = true
                        val restored = runCatching {
                            ApiClient.json.decodeFromString<List<ChatEntry>>(values.chatHistoryJson)
                        }.getOrDefault(emptyList()).takeLast(100)
                        current.copy(
                            preferences = values,
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
                        configured = configured,
                        rememberedDeviceToken = credentials?.deviceToken.takeIf { credentials?.rememberCredentials == true }.orEmpty(),
                        rememberedUserPassword = credentials?.userPassword.orEmpty(),
                        rememberCredentials = credentials?.rememberCredentials == true,
                    )
                }
                if (values.chatSessionId.isBlank() && _state.value.chatSessionId.isNotBlank()) persistChatNow()
                if (configured && secure.get(requireNotNull(account).id, SecureStore.APP_PASSWORD_HASH).isNotBlank()) {
                    // Security is configured; remain locked until explicit authentication.
                } else if (configured) {
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

    fun connect(baseUrl: String, deviceToken: String, username: String, password: String, appPassword: String, rememberCredentials: Boolean) {
        launchBusy {
            val existing = _state.value.preferences.accounts.firstOrNull { it.baseUrl == baseUrl.trimEnd('/') && it.username == username }
            val effectiveToken = deviceToken.ifBlank { existing?.let { repo.credentialState(it.id).deviceToken }.orEmpty() }
            require(effectiveToken.isNotBlank()) { "device token is required" }
            repo.login(baseUrl, effectiveToken, username, password, appPassword, rememberCredentials)
            unlockManager.unlock()
            loadDashboard()
        }
    }

    fun unlockWithPassword(value: String) {
        viewModelScope.launch {
            val account = currentAccount() ?: return@launch
            if (repo.verifyAppPassword(account.id, value)) unlockManager.unlock() else _state.update { it.copy(error = "invalid app password") }
        }
    }

    fun unlockWithBiometric() { unlockManager.unlock() }
    fun lock() { unlockManager.lock() }

    fun sendChat(prompt: String) {
        val pendingAttachments = _state.value.pendingChatAttachments
        if ((prompt.isBlank() && pendingAttachments.isEmpty()) || _state.value.streaming || _state.value.chatClosed || _state.value.chatAttachmentUploading) return
        val assistantId = UUID.randomUUID().toString()
        val sessionId = _state.value.chatSessionId.ifBlank { "app-${UUID.randomUUID()}" }
        val startedAt = System.currentTimeMillis()
        val userEntry = ChatEntry("user-$startedAt", ChatRole.USER, text = prompt, attachments = pendingAttachments)
        val assistantEntry = ChatEntry(assistantId, ChatRole.ASSISTANT, startedAtMs = startedAt)
        _state.update {
            it.copy(
                chatEntries = (it.chatEntries + userEntry + assistantEntry).takeLast(100),
                chatSessionId = sessionId,
                chatClosed = false,
                streaming = true,
                error = "",
                pendingChatAttachments = emptyList(),
            )
        }
        scheduleChatPersist()
        viewModelScope.launch {
            runCatching {
                repo.streamChat(prompt, sessionId, assistantId, pendingAttachments.map { it.path }) { event -> applyStreamEvent(assistantId, event) }
            }.onFailure { failure -> _state.update { it.copy(error = failure.message.orEmpty()) } }
            _state.update { current ->
                current.copy(
                    streaming = false,
                    chatEntries = current.chatEntries.map { entry ->
                        if (entry.id != assistantId || entry.usage?.elapsedMs?.let { it > 0 } == true) entry
                        else entry.copy(
                            usage = (entry.usage ?: ChatUsageUi()).copy(
                                elapsedMs = (System.currentTimeMillis() - entry.startedAtMs).coerceAtLeast(0),
                            ),
                        )
                    },
                )
            }
            persistChatNow()
        }
    }

    fun addChatAttachment(uri: Uri) {
        if (_state.value.chatAttachmentUploading || _state.value.streaming) return
        viewModelScope.launch {
            _state.update { it.copy(chatAttachmentUploading = true, error = "") }
            runCatching {
                require(_state.value.pendingChatAttachments.size < 10) { "单轮最多添加 10 个文件" }
                val sessionId = _state.value.chatSessionId.ifBlank { "app-${UUID.randomUUID()}" }
                val result = repo.uploadFile(uri, "app-chat/$sessionId")
                val root = result as? JsonObject ?: error("上传响应无效")
                val path = (root["path"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                require(path.isNotBlank()) { "上传响应缺少文件路径" }
                val name = path.substringAfterLast('/').substringAfterLast('\\')
                _state.update { current ->
                    current.copy(pendingChatAttachments = (current.pendingChatAttachments + ChatAttachmentUi(name, path)).distinctBy { it.path })
                }
            }.onFailure { failure ->
                _state.update { it.copy(error = failure.message.orEmpty()) }
            }
            _state.update { it.copy(chatAttachmentUploading = false) }
        }
    }

    fun removeChatAttachment(path: String) = _state.update { current ->
        current.copy(pendingChatAttachments = current.pendingChatAttachments.filterNot { it.path == path })
    }

    private fun applyStreamEvent(entryId: String, event: StreamEvent) {
        _state.update { current ->
            current.copy(chatEntries = current.chatEntries.map { entry ->
                if (entry.id != entryId) entry else when (event) {
                    is StreamEvent.Reasoning -> entry.copy(reasoning = entry.reasoning + event.text)
                    is StreamEvent.Text -> entry.copy(text = entry.text + event.text)
                    is StreamEvent.ToolStart -> entry.copy(tools = entry.tools.filterNot { it.callId == event.call.callId } + event.call)
                    is StreamEvent.ToolEnd -> entry.copy(tools = entry.tools.map { tool ->
                        if (tool.callId == event.callId) tool.copy(status = event.status, resultPreview = event.resultPreview) else tool
                    })
                    is StreamEvent.Usage -> entry.copy(usage = event.value)
                    is StreamEvent.Error -> entry.copy(text = entry.text + event.message, tools = entry.tools.map { if (it.status == ToolStatus.RUNNING) it.copy(status = ToolStatus.FAILED) else it })
                    is StreamEvent.Done -> event.value?.let { entry.copy(usage = it) } ?: entry
                }
            })
        }
        scheduleChatPersist()
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
                error = "",
            )
        }
        viewModelScope.launch { persistChatNow() }
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
        _state.update { it.copy(chatEntries = emptyList(), chatSessionId = "app-${UUID.randomUUID()}", chatClosed = false) }
        persistChatNow()
        loadConversationsInternal()
    }

    fun switchConversation(sessionId: String) = launchBusy {
        require(sessionId.isNotBlank()) { "会话标识为空" }
        val payload = repo.conversationMessages(sessionId)
        val entries = parseHistoryMessages(payload)
        _state.update { it.copy(chatEntries = entries.takeLast(100), chatSessionId = sessionId, chatClosed = true, error = "") }
        persistChatNow()
    }

    fun deleteConversation(sessionId: String) = launchBusy {
        repo.deleteConversation(sessionId)
        if (_state.value.chatSessionId == sessionId) {
            _state.update { it.copy(chatEntries = emptyList(), chatSessionId = "app-${UUID.randomUUID()}", chatClosed = false) }
            persistChatNow()
        }
        loadConversationsInternal()
    }

    fun deleteAllConversations() = launchBusy {
        repo.deleteAllConversations()
        _state.update { it.copy(chatEntries = emptyList(), chatSessionId = "app-${UUID.randomUUID()}", chatClosed = false, conversations = null) }
        persistChatNow()
        loadConversationsInternal()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            loadTasksInternal()
            loadStatusInternal()
            loadModulesInternal()
        }
    }

    fun loadTasks() = viewModelScope.launch { loadTasksInternal() }
    fun loadConversations() = viewModelScope.launch { loadConversationsInternal() }
    fun loadStatus() = viewModelScope.launch { loadStatusInternal() }
    fun loadModules() = viewModelScope.launch { loadModulesInternal() }
    fun loadFiles(scope: String, path: String, page: Int = 1) = viewModelScope.launch { loadFileDirectory(scope, path, page) }
    fun uploadFile(uri: Uri, directory: String) = launchBusy {
        repo.uploadFile(uri, directory)
        loadFileDirectory("upload", directory, 1)
    }
    fun loadKnowledge() = viewModelScope.launch { loadValue({ repo.knowledge() }) { value -> copy(knowledge = value) } }
    fun loadModels() = viewModelScope.launch { loadValue({ repo.models() }) { value -> copy(models = value) } }
    fun loadAgentConfig() = viewModelScope.launch { loadValue({ repo.config() }) { value -> copy(agentConfig = value) } }
    fun loadProfileData() = viewModelScope.launch {
        runCatching { repo.avatar() to repo.version() }
            .onSuccess { values -> _state.update { it.copy(avatarBytes = values.first, versions = values.second, error = "") } }
            .onFailure { failure -> _state.update { it.copy(error = failure.message.orEmpty()) } }
    }

    fun taskAction(id: String, action: String) = launchBusy { repo.taskAction(id, action); loadTasksInternal() }
    fun createCron(rawJson: String) = launchBusy { repo.createCron(rawJson); loadTasksInternal() }
    fun updateCron(id: String, rawJson: String) = launchBusy { repo.updateCron(id, rawJson); loadTasksInternal() }
    fun deleteCron(id: String) = launchBusy { repo.deleteCron(id); loadTasksInternal() }
    fun setWhitelist(kind: String, scope: String, name: String, enabled: Boolean) = launchBusy { repo.setWhitelist(kind, scope, name, enabled); loadModulesInternal() }
    fun deleteFile(scope: String, path: String) = launchBusy {
        repo.deleteFile(scope, path)
        val listing = if (scope == "upload") _state.value.uploadFiles else _state.value.generatedFiles
        loadFileDirectory(scope, listing.fileListingPath(), listing.fileListingPage())
    }
    fun downloadFile(scope: String, path: String) = launchBusy { repo.downloadFile(scope, path) }
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
    fun selectModel(model: String) = launchBusy { repo.setModel(model); loadModels() }
    fun patchAgentConfig(changes: JsonObject) = launchBusy { repo.patchConfig(changes); loadAgentConfig() }

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
    fun setTone(value: String) = viewModelScope.launch { prefs.setTone(value) }
    fun setLanguage(value: String) = viewModelScope.launch { prefs.setLanguage(value) }
    fun setNotifications(value: Boolean) = viewModelScope.launch { prefs.setNotifications(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { prefs.setDynamicColor(value) }
    fun setDownloadDirectoryUri(value: String) = viewModelScope.launch { prefs.setDownloadDirectoryUri(value) }
    fun setBiometricEnabled(value: Boolean) = viewModelScope.launch { prefs.setBiometricEnabled(value) }
    fun changeAppPassword(oldPassword: String, newPassword: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val account = currentAccount()
            val changed = account != null && newPassword.length >= 4 && repo.changeAppPassword(account.id, oldPassword, newPassword)
            onResult(changed)
        }
    }
    fun clearError() = _state.update { it.copy(error = "") }

    private suspend fun loadTasksInternal() {
        runCatching {
            val plans = repo.taskPlans()
            val crons = repo.cron()
            repo.updateWidgetSummary()
            _state.update { it.copy(tasks = plans, cron = crons, error = "") }
        }.onFailure { _state.update { state -> state.copy(error = it.message.orEmpty()) } }
    }

    private suspend fun loadStatusInternal() = loadValue({ repo.status() }) { value -> copy(status = value) }
    private suspend fun loadConversationsInternal() = loadValue({ repo.conversations() }) { value -> copy(conversations = value) }
    private suspend fun loadModulesInternal() {
        runCatching { repo.expandsData() to repo.senses() }
            .onSuccess { values -> _state.update { it.copy(expands = values.first, senses = values.second, error = "") } }
            .onFailure { failure -> _state.update { it.copy(error = failure.message.orEmpty()) } }
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
            .onFailure { failure -> _state.update { it.copy(error = failure.message.orEmpty()) } }
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
            .onFailure { failure -> _state.update { it.copy(error = failure.message.orEmpty()) } }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = "") }
            runCatching { block() }.onFailure { failure -> _state.update { it.copy(error = failure.message.orEmpty()) } }
            _state.update { it.copy(busy = false) }
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
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(context, MainActivity::class.java).setData(android.net.Uri.parse("kemo://task/${UUID.randomUUID()}"))
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val taskEvent = event.type.startsWith("task_plan") || event.type.startsWith("cron")
        val notification = NotificationCompat.Builder(context, if (taskEvent) KemoApp.TASK_CHANNEL else KemoApp.SYSTEM_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(event.type)
            .setContentText(context.getString(R.string.notification_default))
            .setContentIntent(pending).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(event.type.hashCode(), notification)
    }

    override fun onCleared() { eventSocket?.stop(); super.onCleared() }

    private fun parseHistoryMessages(payload: JsonElement): List<ChatEntry> {
        val messages = when (payload) {
            is JsonArray -> payload
            is JsonObject -> payload["messages"] as? JsonArray ?: payload["items"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return messages.mapIndexedNotNull { index, element ->
            val item = element as? JsonObject ?: return@mapIndexedNotNull null
            val role = (item["role"] as? JsonPrimitive)?.contentOrNull.orEmpty().lowercase()
            val textElement = item["content"] ?: item["text"] ?: item["message"]
            val text = when (textElement) {
                is JsonPrimitive -> textElement.contentOrNull.orEmpty()
                null -> ""
                else -> textElement.toString()
            }
            if (text.isBlank()) return@mapIndexedNotNull null
            ChatEntry(
                id = "history-${_state.value.chatSessionId}-$index",
                role = if (role == "user") ChatRole.USER else ChatRole.ASSISTANT,
                text = text,
            )
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
        private val TEXT_PREVIEW_EXTENSIONS = setOf(
            "txt", "md", "markdown", "json", "xml", "csv", "tsv", "log", "ini", "conf", "yaml", "yml",
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "css", "sql", "sh", "ps1",
        )
    }
}
