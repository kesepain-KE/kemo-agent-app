package com.kesepain.kemoapp.data.repo

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.FileProvider
import com.kesepain.kemoapp.data.local.AccountConfig
import com.kesepain.kemoapp.data.local.AccountTransferCodec
import com.kesepain.kemoapp.data.local.AccountTransferPayload
import com.kesepain.kemoapp.data.local.Prefs
import com.kesepain.kemoapp.data.local.SecureStore
import com.kesepain.kemoapp.data.remote.ApiBundle
import com.kesepain.kemoapp.data.remote.ApiClient
import com.kesepain.kemoapp.data.remote.ApiSecrets
import com.kesepain.kemoapp.data.remote.AuthResponseDto
import com.kesepain.kemoapp.data.remote.ChatRequestDto
import com.kesepain.kemoapp.data.stream.ChatStreamParser
import com.kesepain.kemoapp.data.stream.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.BufferedSink
import okio.source
import java.io.File
import java.util.UUID

data class FilePreviewPayload(val name: String, val mimeType: String, val bytes: ByteArray)

data class ManagedRunReplayEvent(val eventId: Long, val event: StreamEvent)

data class ManagedRunSnapshot(
    val runId: String,
    val sessionId: String,
    val status: String,
    val terminal: Boolean,
    val recoverable: Boolean,
    val lastEventId: Long,
    val createdAt: Long,
    val prompt: String,
    val uploadedFiles: List<String>,
    val events: List<ManagedRunReplayEvent>,
    val error: String = "",
)

internal fun parseManagedRunSnapshot(value: JsonElement): ManagedRunSnapshot {
    val root = value as? JsonObject ?: error("invalid run snapshot")
    fun text(name: String): String = (root[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun long(name: String): Long = (root[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
    fun bool(name: String): Boolean = (root[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true
    val parser = ChatStreamParser()
    val events = (root["events"] as? JsonArray).orEmpty().mapNotNull { raw ->
        val item = raw as? JsonObject ?: return@mapNotNull null
        val eventId = (item["event_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
        val data = (item["data"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        parser.parsePayload(data)?.let { ManagedRunReplayEvent(eventId, it) }
    }
    return ManagedRunSnapshot(
        runId = text("run_id"),
        sessionId = text("session_id"),
        status = text("status"),
        terminal = bool("terminal"),
        recoverable = bool("recoverable"),
        lastEventId = long("last_event_id"),
        createdAt = long("created_at"),
        prompt = text("prompt"),
        uploadedFiles = (root["uploaded_files"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        },
        events = events,
        error = text("error"),
    )
}

data class StoredCredentialState(
    val deviceToken: String = "",
    val userPassword: String = "",
    val sessionToken: String = "",
    val rememberCredentials: Boolean = false,
    val sessionExpiresAt: Long = 0L,
)

class KemoRepository(private val context: Context) {
    private val prefs = Prefs(context)
    private val secure = SecureStore(context)
    /** Session-only credentials are isolated per account for in-process account switching. */
    private val ephemeralCredentials = mutableMapOf<String, StoredCredentialState>()

    /** 携带发起请求的账号 ID，避免切换期间旧请求误清除新账号会话。 */
    var onSessionExpired: ((accountId: String) -> Unit)? = null

    suspend fun login(
        displayName: String,
        baseUrl: String,
        deviceToken: String,
        username: String,
        password: String,
        appPassword: String,
        rememberCredentials: Boolean,
        makeCurrent: Boolean = true,
    ): AccountConfig = withContext(Dispatchers.IO) {
        val account = AccountConfig(
            id = accountId(baseUrl, username),
            baseUrl = baseUrl.trimEnd('/'),
            username = username,
            displayName = displayName.trim(),
        )
        val bundle = ApiClient.create(account, ApiSecrets(deviceToken, ""))
        bundle.client.newCall(
            Request.Builder().url(bundle.baseUrl + "v1/auth/device").post("{}".toRequestBody(ApiClient.jsonMediaType)).build()
        ).execute().use { if (!it.isSuccessful) error("device authentication failed (${it.code})") }
        val loginJson = buildJsonObject { put("username", username); put("password", password) }
        val auth = bundle.client.newCall(
            Request.Builder().url(bundle.baseUrl + "v1/auth/user")
                .post(ApiClient.json.encodeToString(loginJson).toRequestBody(ApiClient.jsonMediaType)).build()
        ).execute().use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) error("user authentication failed (${it.code})")
            ApiClient.json.decodeFromString<AuthResponseDto>(text)
        }
        val now = System.currentTimeMillis() / 1000
        val sessionExpiresAt = auth.expiresAt.takeIf { it > now } ?: (now + DEFAULT_SESSION_AGE_SECONDS)
        val credentialsExpireAt = if (rememberCredentials) now + MAX_CREDENTIAL_AGE_SECONDS else sessionExpiresAt
        if (rememberCredentials) {
            synchronized(this@KemoRepository) { ephemeralCredentials.remove(account.id) }
            secure.update(
                account.id,
                values = mapOf(
                    SecureStore.DEVICE_TOKEN to deviceToken,
                    SecureStore.USER_PASSWORD to password,
                    SecureStore.SESSION_TOKEN to auth.sessionToken,
                    SecureStore.SESSION_EXPIRES_AT to sessionExpiresAt.toString(),
                    SecureStore.REMEMBER_CREDENTIALS to true.toString(),
                    SecureStore.CREDENTIALS_EXPIRE_AT to credentialsExpireAt.toString(),
                ),
            )
        } else {
            synchronized(this@KemoRepository) {
                ephemeralCredentials[account.id] = StoredCredentialState(
                    deviceToken = deviceToken,
                    sessionToken = auth.sessionToken,
                    rememberCredentials = false,
                    sessionExpiresAt = sessionExpiresAt,
                )
            }
            clearAuthentication(account.id)
        }
        if (appPassword.isNotBlank()) secure.put(account.id, SecureStore.APP_PASSWORD_HASH, sha256(appPassword))
        prefs.saveAccount(account, makeCurrent = makeCurrent)
        account
    }

    suspend fun bundle(accountId: String? = null): Pair<AccountConfig, ApiBundle> {
        val snapshot = prefs.snapshot()
        val targetAccountId = accountId ?: snapshot.currentAccountId
        val account = snapshot.accounts.firstOrNull { it.id == targetAccountId } ?: error("no configured account")
        val credentials = credentialState(account.id)
        val device = credentials.deviceToken
        val session = credentials.sessionToken
        if (device.isBlank() || session.isBlank()) error("account requires login")
        return account to ApiClient.create(account, ApiSecrets(device, session))
    }

    suspend fun credentialState(accountId: String): StoredCredentialState {
        val now = System.currentTimeMillis() / 1000
        synchronized(this) { ephemeralCredentials[accountId] }
            ?.let { ephemeral ->
                if (ephemeral.sessionExpiresAt > now) return ephemeral
                synchronized(this) {
                    ephemeralCredentials.remove(accountId)
                }
            }

        val remember = secure.get(accountId, SecureStore.REMEMBER_CREDENTIALS).toBooleanStrictOrNull() == true
        if (!remember) {
            val legacyValues = listOf(
                SecureStore.DEVICE_TOKEN,
                SecureStore.USER_PASSWORD,
                SecureStore.SESSION_TOKEN,
                SecureStore.SESSION_EXPIRES_AT,
                SecureStore.CREDENTIALS_EXPIRE_AT,
            ).any { secure.get(accountId, it).isNotBlank() }
            if (legacyValues) clearAuthentication(accountId)
            return StoredCredentialState()
        }
        val credentialsExpireAt = secure.get(accountId, SecureStore.CREDENTIALS_EXPIRE_AT).toLongOrNull() ?: 0L
        if (credentialsExpireAt > 0L && credentialsExpireAt <= now) {
            clearAuthentication(accountId)
            return StoredCredentialState()
        }

        var session = secure.get(accountId, SecureStore.SESSION_TOKEN)
        val sessionExpiresAt = secure.get(accountId, SecureStore.SESSION_EXPIRES_AT).toLongOrNull() ?: 0L
        if (sessionExpiresAt > 0L && sessionExpiresAt <= now) {
            val expiredNames = mutableSetOf(SecureStore.SESSION_TOKEN, SecureStore.SESSION_EXPIRES_AT)
            session = ""
            if (!remember) {
                expiredNames += SecureStore.DEVICE_TOKEN
                expiredNames += SecureStore.CREDENTIALS_EXPIRE_AT
            }
            secure.clear(accountId, expiredNames)
        }

        return StoredCredentialState(
            deviceToken = secure.get(accountId, SecureStore.DEVICE_TOKEN),
            userPassword = secure.get(accountId, SecureStore.USER_PASSWORD),
            sessionToken = session,
            rememberCredentials = true,
            sessionExpiresAt = sessionExpiresAt,
        )
    }

    suspend fun secrets(accountId: String): ApiSecrets {
        val credentials = credentialState(accountId)
        return ApiSecrets(credentials.deviceToken, credentials.sessionToken)
    }

    /**
     * Validates a saved account before publishing an in-App account switch. If the
     * bridge has restarted and invalidated its in-memory session, remembered user
     * credentials are used to obtain a fresh session without opening the edit page.
     */
    suspend fun ensureAccountSession(accountId: String): StoredCredentialState = withContext(Dispatchers.IO) {
        val account = prefs.snapshot().accounts.firstOrNull { it.id == accountId }
            ?: error("account not found")
        val credentials = credentialState(accountId)
        require(credentials.deviceToken.isNotBlank()) { "account requires login" }

        if (credentials.sessionToken.isNotBlank()) {
            val response = ApiClient.create(
                account,
                ApiSecrets(credentials.deviceToken, credentials.sessionToken),
            ).rest.conversations(limit = 1)
            val code = response.code()
            response.body()?.close()
            if (response.isSuccessful) return@withContext credentials
            if (code != 401 && code != 403) error("session validation failed ($code)")
            // The bridge keeps sessions in process memory. A bridge/framework restart
            // can therefore invalidate a locally unexpired session. Drop only that
            // stale session before authenticating again with remembered credentials.
            clearSession(accountId)
        }

        val reconnectCredentials = credentialState(accountId)
        require(reconnectCredentials.userPassword.isNotBlank()) { "account requires login" }
        login(
            displayName = account.displayName,
            baseUrl = account.baseUrl,
            deviceToken = reconnectCredentials.deviceToken,
            username = account.username,
            password = reconnectCredentials.userPassword,
            appPassword = "",
            rememberCredentials = true,
            makeCurrent = false,
        )
        credentialState(accountId).also { refreshed ->
            require(refreshed.deviceToken.isNotBlank() && refreshed.sessionToken.isNotBlank()) {
                "account requires login"
            }
        }
    }

    suspend fun exportAccount(accountId: String, password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val account = prefs.snapshot().accounts.firstOrNull { it.id == accountId }
            ?: error("account not found")
        val credentials = credentialState(accountId)
        AccountTransferCodec.encrypt(
            AccountTransferPayload(
                displayName = account.displayName,
                baseUrl = account.baseUrl,
                username = account.username,
                deviceToken = credentials.deviceToken,
                userPassword = credentials.userPassword,
                sessionToken = credentials.sessionToken,
                sessionExpiresAt = credentials.sessionExpiresAt,
                exportedAt = System.currentTimeMillis() / 1000,
            ),
            password,
        )
    }

    suspend fun importAccount(fileBytes: ByteArray, password: CharArray): AccountConfig = withContext(Dispatchers.IO) {
        val payload = AccountTransferCodec.decrypt(fileBytes, password)
        val baseUrl = payload.baseUrl.trim().trimEnd('/')
        val username = payload.username.trim()
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) { "invalid server address" }
        require(username.isNotBlank()) { "invalid account username" }
        val account = AccountConfig(
            id = accountId(baseUrl, username),
            baseUrl = baseUrl,
            username = username,
            displayName = payload.displayName.trim(),
        )
        val snapshot = prefs.snapshot()
        val hasCredentials = payload.deviceToken.isNotBlank() ||
            payload.userPassword.isNotBlank() ||
            payload.sessionToken.isNotBlank()
        if (hasCredentials) {
            val now = System.currentTimeMillis() / 1000
            val values = mutableMapOf(
                SecureStore.REMEMBER_CREDENTIALS to true.toString(),
                SecureStore.CREDENTIALS_EXPIRE_AT to (now + MAX_CREDENTIAL_AGE_SECONDS).toString(),
            )
            payload.deviceToken.takeIf(String::isNotBlank)?.let { values[SecureStore.DEVICE_TOKEN] = it }
            payload.userPassword.takeIf(String::isNotBlank)?.let { values[SecureStore.USER_PASSWORD] = it }
            if (payload.sessionToken.isNotBlank() && payload.sessionExpiresAt > now) {
                values[SecureStore.SESSION_TOKEN] = payload.sessionToken
                values[SecureStore.SESSION_EXPIRES_AT] = payload.sessionExpiresAt.toString()
            }
            secure.update(
                account.id,
                values = values,
                remove = setOf(
                    SecureStore.DEVICE_TOKEN,
                    SecureStore.USER_PASSWORD,
                    SecureStore.SESSION_TOKEN,
                    SecureStore.SESSION_EXPIRES_AT,
                    SecureStore.REMEMBER_CREDENTIALS,
                    SecureStore.CREDENTIALS_EXPIRE_AT,
                ),
            )
        }
        // Keep the user's current account when importing into an existing list;
        // a first imported account becomes current so the App remains usable.
        prefs.saveAccount(account, makeCurrent = snapshot.accounts.isEmpty())
        account
    }

    suspend fun appPasswordConfigured(accountId: String): Boolean = secure.get(accountId, SecureStore.APP_PASSWORD_HASH).isNotBlank()
    suspend fun verifyAppPassword(accountId: String, value: String): Boolean = secure.get(accountId, SecureStore.APP_PASSWORD_HASH) == sha256(value)
    suspend fun deleteAccount(accountId: String) {
        synchronized(this) {
            ephemeralCredentials.remove(accountId)
        }
        secure.clear(
            accountId,
            listOf(
                SecureStore.DEVICE_TOKEN,
                SecureStore.USER_PASSWORD,
                SecureStore.SESSION_TOKEN,
                SecureStore.SESSION_EXPIRES_AT,
                SecureStore.REMEMBER_CREDENTIALS,
                SecureStore.CREDENTIALS_EXPIRE_AT,
                SecureStore.APP_PASSWORD_HASH,
            ),
        )
        prefs.removeAccount(accountId)
    }

    suspend fun clearSession(accountId: String) {
        synchronized(this) {
            ephemeralCredentials.remove(accountId)
        }
        val names = mutableSetOf(SecureStore.SESSION_TOKEN, SecureStore.SESSION_EXPIRES_AT)
        if (secure.get(accountId, SecureStore.REMEMBER_CREDENTIALS).toBooleanStrictOrNull() != true) {
            names += SecureStore.DEVICE_TOKEN
            names += SecureStore.CREDENTIALS_EXPIRE_AT
        }
        secure.clear(accountId, names)
    }

    private suspend fun clearAuthentication(accountId: String) {
        listOf(
            SecureStore.DEVICE_TOKEN,
            SecureStore.USER_PASSWORD,
            SecureStore.SESSION_TOKEN,
            SecureStore.SESSION_EXPIRES_AT,
            SecureStore.REMEMBER_CREDENTIALS,
            SecureStore.CREDENTIALS_EXPIRE_AT,
        ).let { secure.clear(accountId, it) }
    }

    suspend fun health(): JsonElement = call { it.health() }
    suspend fun conversations(limit: Int = 50): JsonElement = call { it.conversations(limit) }

    suspend fun activeConversation(clientId: String): JsonElement = call { it.activeConversation(clientId) }
    suspend fun deleteAllConversations(): JsonElement = call { it.deleteAllConversations() }
    suspend fun conversationMessages(id: String): JsonElement = call { it.conversationMessages(id) }
    suspend fun deleteConversation(id: String, clientId: String = ""): JsonElement =
        call { it.deleteConversation(id, clientId) }
    suspend fun closeConversation(id: String, clientId: String = ""): JsonElement =
        call { it.closeConversation(id, clientId) }
    suspend fun compressConversation(id: String): JsonElement = call { it.compressConversation(id) }
    suspend fun undoLastRound(id: String, expectedRound: Int, prompt: String): JsonElement {
        val body = ApiClient.json.encodeToString(
            buildJsonObject {
                put("expected_round", expectedRound)
                put("prompt", prompt)
            },
        ).toRequestBody(ApiClient.jsonMediaType)
        return call { it.undoLastRound(id, body) }
    }
    suspend fun submitGuidance(runId: String, guidance: String, guidanceId: String, uploadedFiles: List<String>): JsonElement {
        val body = ApiClient.json.encodeToString(
            buildJsonObject {
                put("run_id", runId)
                put("guidance", guidance)
                put("guidance_id", guidanceId)
                put("uploaded_files", kotlinx.serialization.json.buildJsonArray {
                    uploadedFiles.forEach { add(JsonPrimitive(it)) }
                })
            },
        ).toRequestBody(ApiClient.jsonMediaType)
        return call { it.submitGuidance(body) }
    }
    suspend fun cancelRun(runId: String): JsonElement = call { it.cancelRun(runId) }

    suspend fun activeRuns(clientId: String, sessionId: String = ""): JsonElement =
        call { it.activeRuns(clientId, sessionId) }

    suspend fun runSnapshot(runId: String, after: Long = 0): ManagedRunSnapshot =
        parseManagedRunSnapshot(call { it.runSnapshot(runId, after) })
    suspend fun taskPlans(): JsonElement = call { it.taskPlans() }
    suspend fun cron(): JsonElement = call { it.cron() }
    suspend fun status(sessionId: String = "", clientId: String = ""): JsonElement = call { it.status(sessionId, clientId) }
    suspend fun expands(): JsonElement = call { it.expands() }
    suspend fun expandsData(): JsonElement = call { it.expandsData() }
    suspend fun senses(): JsonElement = call { it.senses() }
    suspend fun files(scope: String, path: String = "", page: Int = 1): JsonElement = call { it.files(scope, path, page) }
    suspend fun uploadFile(uri: Uri, directory: String): JsonElement = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = uri.lastPathSegment.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        var length = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex).orEmpty()
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) length = cursor.getLong(sizeIndex)
            }
        }
        val safeName = displayName.substringAfterLast('/').substringAfterLast('\\').trim().ifBlank { "upload.bin" }.take(240)
        require(length < 0 || length <= MAX_UPLOAD_BYTES) { "文件超过最大限制 80 MB" }
        val mediaType = resolver.getType(uri)?.toMediaTypeOrNull()
        val body = object : RequestBody() {
            override fun contentType() = mediaType
            override fun contentLength(): Long = length
            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri) ?: error("无法读取所选文件")
                input.use { sink.writeAll(it.source()) }
            }
        }
        val part = MultipartBody.Part.createFormData("file", safeName, body)
        val (account, bundle) = bundle()
        val response = bundle.rest.uploadFile(directory.trim('/'), part)
        val text = response.body()?.string().orEmpty()
        if (!response.isSuccessful) {
            if (response.code() == 401 || response.code() == 403) onSessionExpired?.invoke(account.id)
            error("upload failed (${response.code()})")
        }
        if (text.isBlank()) JsonObject(emptyMap()) else ApiClient.json.parseToJsonElement(text)
    }

    suspend fun cacheChatMedia(path: String, name: String, scope: String = "download"): String = withContext(Dispatchers.IO) {
        val (account, bundle) = bundle()
        val response = bundle.rest.downloadFile(scope = scope, path = path)
        if (!response.isSuccessful) {
            if (response.code() == 401 || response.code() == 403) onSessionExpired?.invoke(account.id)
            error("media download failed (${response.code()})")
        }
        val body = response.body() ?: error("empty media response")
        val declaredLength = body.contentLength()
        require(declaredLength < 0 || declaredLength <= MAX_CHAT_MEDIA_CACHE_BYTES) { "媒体文件过大，请下载后查看" }
        val safeName = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(180)
            .ifBlank { "media.bin" }
        val directory = File(context.cacheDir, "chat-media").apply { mkdirs() }
        val target = File(directory, "${sha256("$scope:$path").take(16)}-$safeName")
        body.byteStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_CHAT_MEDIA_CACHE_BYTES) { "媒体文件过大，请下载后查看" }
                    output.write(buffer, 0, count)
                }
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target).toString()
    }
    suspend fun knowledge(): JsonElement = call { it.knowledge() }
    suspend fun searchKnowledge(query: String): JsonElement = call { it.knowledgeSearch(query) }
    suspend fun models(refresh: Boolean = false): JsonElement = call { it.models(refresh) }
    suspend fun modelCapabilities(model: String, refresh: Boolean = false): JsonElement = call { it.modelCapabilities(model, refresh) }
    suspend fun config(): JsonElement = call { it.config() }
    suspend fun version(): JsonElement = call { it.version() }

    suspend fun avatar(): ByteArray? = withContext(Dispatchers.IO) {
        val (account, bundle) = bundle()
        val response = bundle.rest.avatar()
        if (response.code() == 204) return@withContext null
        if (!response.isSuccessful) {
            if (response.code() == 401 || response.code() == 403) onSessionExpired?.invoke(account.id)
            error("avatar request failed (${response.code()})")
        }
        response.body()?.bytes()
    }

    suspend fun taskAction(id: String, action: String): JsonElement = call { it.taskAction(id, action) }
    suspend fun createCron(rawJson: String): JsonElement = call { it.createCron(rawJson.toRequestBody(ApiClient.jsonMediaType)) }
    suspend fun updateCron(id: String, rawJson: String): JsonElement = call { it.updateCron(id, rawJson.toRequestBody(ApiClient.jsonMediaType)) }
    suspend fun deleteCron(id: String): JsonElement = call { it.deleteCron(id) }
    suspend fun setWhitelist(kind: String, scope: String, name: String, enabled: Boolean): JsonElement {
        val body = ApiClient.json.encodeToString(buildJsonObject { put("kind", kind); put("scope", scope); put("name", name); put("enabled", enabled) }).toRequestBody(ApiClient.jsonMediaType)
        return call { it.setWhitelist(body) }
    }
    suspend fun deleteFile(scope: String, path: String): JsonElement = call { it.deleteFile(scope, path) }
    suspend fun previewFile(scope: String, path: String, name: String): FilePreviewPayload = withContext(Dispatchers.IO) {
        val (account, bundle) = bundle()
        val response = bundle.rest.downloadFile(scope = scope, path = path)
        if (!response.isSuccessful) {
            if (response.code() == 401 || response.code() == 403) onSessionExpired?.invoke(account.id)
            error("preview failed (${response.code()})")
        }
        val body = response.body() ?: error("empty preview")
        val declaredLength = body.contentLength()
        require(declaredLength < 0L || declaredLength <= MAX_FILE_PREVIEW_BYTES) { "preview file is too large" }
        val output = java.io.ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_FILE_PREVIEW_BYTES) { "preview file is too large" }
                output.write(buffer, 0, count)
            }
        }
        FilePreviewPayload(name, body.contentType()?.toString().orEmpty(), output.toByteArray())
    }
    suspend fun downloadFile(scope: String, path: String): String = withContext(Dispatchers.IO) {
        val (account, bundle) = bundle()
        val response = bundle.rest.downloadFile(scope = scope, path = path)
        if (!response.isSuccessful) {
            if (response.code() == 401 || response.code() == 403) onSessionExpired?.invoke(account.id)
            error("download failed (${response.code()})")
        }
        val body = response.body() ?: error("empty download")
        val fileName = path.substringAfterLast('/').substringAfterLast('\\').ifBlank { "download.bin" }
        val mimeType = body.contentType()?.toString() ?: "application/octet-stream"
        val configuredTree = prefs.snapshot().downloadDirectoryUri.takeIf(String::isNotBlank)
        if (configuredTree != null) {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(configuredTree)) ?: error("下载目录不可访问")
            var candidate = fileName
            var index = 2
            while (tree.findFile(candidate) != null) {
                val dot = fileName.lastIndexOf('.')
                val stem = if (dot > 0) fileName.substring(0, dot) else fileName
                val suffix = if (dot > 0) fileName.substring(dot) else ""
                candidate = "$stem ($index)$suffix"
                index += 1
            }
            val target = tree.createFile(mimeType, candidate) ?: error("无法在所选目录创建文件")
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            } ?: error("无法写入下载文件")
            return@withContext target.uri.toString()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val target = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建系统下载文件")
            try {
                context.contentResolver.openOutputStream(target, "w")?.use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                } ?: error("无法写入系统下载目录")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(target, values, null, null)
                return@withContext target.toString()
            } catch (failure: Throwable) {
                context.contentResolver.delete(target, null, null)
                throw failure
            }
        }
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        directory.mkdirs()
        val target = java.io.File(directory, fileName)
        body.byteStream().use { input -> target.outputStream().use(input::copyTo) }
        target.absolutePath
    }

    suspend fun setModel(model: String): JsonElement {
        val body = ApiClient.json.encodeToString(buildJsonObject { put("model", model) }).toRequestBody(ApiClient.jsonMediaType)
        return call { it.setModel(body) }
    }

    suspend fun patchConfig(changes: JsonObject): JsonElement {
        val body = ApiClient.json.encodeToString(buildJsonObject { put("changes", changes) }).toRequestBody(ApiClient.jsonMediaType)
        return call { it.patchConfig(body) }
    }

    suspend fun streamChat(prompt: String, sessionId: String, runId: String, clientId: String, uploadedFiles: List<String>, reasoningEffort: String, onEvent: (StreamEvent) -> Unit) = withContext(Dispatchers.IO) {
        val (account, bundle) = bundle()
        val parser = ChatStreamParser()
        val body = ChatRequestDto(
            sessionId = sessionId,
            prompt = prompt,
            runId = runId,
            clientId = clientId,
            uploadedFiles = uploadedFiles,
            reasoningEffort = reasoningEffort,
        )
        // An SSE response can legitimately wait longer than OkHttp's 10-second default
        // read timeout before the first token arrives. Use an unbounded read timeout for
        // this single streaming call while keeping normal REST calls protected by the
        // default client timeout.
        val streamClient = bundle.client.newBuilder()
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        var cursor = 0L
        var terminal = false
        var initialFailure: Throwable? = null
        try {
            streamClient.newCall(ApiClient.chatRequest(bundle, body)).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401 || response.code == 403) onSessionExpired?.invoke(account.id)
                    error("chat failed (${response.code})")
                }
                val consumed = consumeSse(response, parser) { _, event -> onEvent(event) }
                cursor = maxOf(cursor, consumed.lastEventId)
                terminal = consumed.terminal
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            initialFailure = failure
        }

        // The bridge owns the upstream run independently. A mobile socket loss
        // therefore means "reattach", never "mark the assistant response as
        // failed". Snapshot replay closes any event gap before the new stream.
        while (!terminal) {
            currentCoroutineContext().ensureActive()
            val snapshot = try {
                runSnapshot(runId, cursor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (initialFailure != null && failure.message.orEmpty().contains("(404)")) throw initialFailure
                delay(MANAGED_RUN_RECONNECT_DELAY_MS)
                continue
            }
            initialFailure = null
            snapshot.events.forEach { replay ->
                cursor = maxOf(cursor, replay.eventId)
                onEvent(replay.event)
                if (replay.event is StreamEvent.Done || replay.event is StreamEvent.Error) terminal = true
            }
            cursor = maxOf(cursor, snapshot.lastEventId)
            if (snapshot.terminal) break
            try {
                var resumeTerminal = false
                resumeRun(runId, cursor) { eventId, event ->
                    cursor = maxOf(cursor, eventId)
                    onEvent(event)
                    if (event is StreamEvent.Done || event is StreamEvent.Error) resumeTerminal = true
                }
                terminal = resumeTerminal
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                delay(MANAGED_RUN_RECONNECT_DELAY_MS)
            }
        }
    }

    suspend fun resumeRun(
        runId: String,
        after: Long,
        onEvent: (eventId: Long, event: StreamEvent) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val (account, bundle) = bundle()
        val parser = ChatStreamParser()
        val url = bundle.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("v1/runs")
            .addPathSegment(runId)
            .addPathSegment("stream")
            .addQueryParameter("after", after.coerceAtLeast(0).toString())
            .build()
        val request = Request.Builder().url(url).get().header("Accept", "text/event-stream").build()
        val streamClient = bundle.client.newBuilder()
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        streamClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) onSessionExpired?.invoke(account.id)
                error("run resume failed (${response.code})")
            }
            consumeSse(response, parser, onEvent)
            Unit
        }
    }

    private data class SseConsumeResult(val lastEventId: Long, val terminal: Boolean)

    private fun consumeSse(
        response: okhttp3.Response,
        parser: ChatStreamParser,
        onEvent: (eventId: Long, event: StreamEvent) -> Unit,
    ): SseConsumeResult {
        val source = response.body?.source() ?: error("empty chat stream")
        val dataLines = mutableListOf<String>()
        var eventId = 0L
        var lastEventId = 0L
        var terminal = false
        fun dispatchFrame() {
            if (dataLines.isEmpty()) return
            parser.parsePayload(dataLines.joinToString("\n"))?.let { event ->
                onEvent(eventId, event)
                lastEventId = maxOf(lastEventId, eventId)
                if (event is StreamEvent.Done || event is StreamEvent.Error) terminal = true
            }
            dataLines.clear()
            eventId = 0L
        }
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isBlank() -> dispatchFrame()
                line.startsWith("id:") -> eventId = line.removePrefix("id:").trim().toLongOrNull() ?: 0L
                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
            }
        }
        dispatchFrame()
        return SseConsumeResult(lastEventId, terminal)
    }

    suspend fun changeAppPassword(accountId: String, oldPassword: String, newPassword: String): Boolean {
        if (appPasswordConfigured(accountId) && !verifyAppPassword(accountId, oldPassword)) return false
        secure.put(accountId, SecureStore.APP_PASSWORD_HASH, sha256(newPassword))
        return true
    }

    suspend fun updateWidgetSummary() {
        val data = taskPlans()
        val items = flattenObjects(data)
        val pending = items.count { (it["status"] as? JsonPrimitive)?.contentOrNull in setOf("pending", "approved", "awaiting_approval") }
        val latest = items.firstNotNullOfOrNull {
            (it["title"] as? JsonPrimitive)?.contentOrNull ?: (it["name"] as? JsonPrimitive)?.contentOrNull
        }.orEmpty()
        prefs.setWidgetSummary(pending, latest)
    }

    private suspend fun call(block: suspend (com.kesepain.kemoapp.data.remote.RestApi) -> retrofit2.Response<okhttp3.ResponseBody>): JsonElement {
        val (account, bundle) = bundle()
        val response = block(bundle.rest)
        val text = response.body()?.string().orEmpty()
        if (!response.isSuccessful) {
            if (response.code() == 401 || response.code() == 403) onSessionExpired?.invoke(account.id)
            error("request failed (${response.code()})")
        }
        return if (text.isBlank()) JsonObject(emptyMap()) else ApiClient.json.parseToJsonElement(text)
    }

    companion object {
        const val MAX_CREDENTIAL_AGE_SECONDS = 7L * 24L * 60L * 60L
        private const val DEFAULT_SESSION_AGE_SECONDS = 2L * 60L * 60L
        private const val MAX_UPLOAD_BYTES = 80L * 1024L * 1024L
        private const val MAX_CHAT_MEDIA_CACHE_BYTES = 200L * 1024L * 1024L
        private const val MANAGED_RUN_RECONNECT_DELAY_MS = 1_500L
        private const val MAX_FILE_PREVIEW_BYTES = 24L * 1024L * 1024L
        fun accountId(baseUrl: String, username: String) = sha256("${baseUrl.trimEnd('/')}|$username").take(20)
        fun flattenObjects(value: JsonElement): List<JsonObject> {
            val result = mutableListOf<JsonObject>()
            fun walk(item: JsonElement) {
                when (item) {
                    is JsonObject -> { result += item; item.values.forEach(::walk) }
                    is kotlinx.serialization.json.JsonArray -> item.forEach(::walk)
                    else -> Unit
                }
            }
            walk(value)
            return result
        }
        private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
