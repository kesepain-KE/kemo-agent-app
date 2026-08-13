package com.kesepain.kemoapp.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import android.os.Build
import com.kesepain.kemoapp.device.DeviceCapabilityRegistry

class EventSocket(
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val url: String,
    private val deviceToken: String,
    private val sessionToken: String,
    private val deviceId: String,
    private val onEvent: (EventDto) -> Unit,
    private val onOpen: () -> Unit = {},
    private val onAuthenticationFailed: () -> Unit = {},
) {
    private var socket: WebSocket? = null
    private var retryJob: Job? = null
    private var stopped = false
    private var attempt = 0

    fun start() { stopped = false; connect() }
    fun stop() { stopped = true; retryJob?.cancel(); socket?.close(1000, "app stopped") }

    fun sendDeviceResult(commandId: String, status: String, detail: Map<String, String> = emptyMap()): Boolean {
        val payload = buildJsonObject {
            put("type", "device.command.result")
            put("data", buildJsonObject {
                put("command_id", commandId)
                put("status", status)
                put("detail", JsonObject(detail.mapValues { (_, value) -> kotlinx.serialization.json.JsonPrimitive(value) }))
            })
        }
        return socket?.send(ApiClient.json.encodeToString(payload)) == true
    }

    private fun connect() {
        val request = Request.Builder().url(url.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://").trimEnd('/') + "/v1/ws")
            .header("Authorization", "Bearer $deviceToken")
            .header("X-Kemo-Session", sessionToken)
            .header("X-Kemo-Device-Id", deviceId)
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
                webSocket.send(ApiClient.json.encodeToString(DeviceCapabilityRegistry.payload()))
                onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { ApiClient.json.decodeFromString<EventDto>(text) }.onSuccess {
                    if (it.type == "ping") webSocket.send("{\"type\":\"pong\"}") else onEvent(it)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // A bridge restart invalidates its in-memory sessions. Let the
                // account layer refresh the saved session instead of requiring the
                // user to open the account editor and save the same credentials.
                if (response?.code == 403 || response?.code == 401) {
                    stop()
                    onAuthenticationFailed()
                    return
                }
                reconnect()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (!stopped) reconnect() }
        })
    }

    private fun reconnect() {
        if (stopped || retryJob?.isActive == true) return
        val delayMs = (1000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
        attempt++
        retryJob = scope.launch { delay(delayMs); connect() }
    }
}
