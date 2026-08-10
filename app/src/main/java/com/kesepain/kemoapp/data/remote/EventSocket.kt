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

class EventSocket(
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val url: String,
    private val deviceToken: String,
    private val sessionToken: String,
    private val deviceId: String,
    private val onEvent: (EventDto) -> Unit,
) {
    private var socket: WebSocket? = null
    private var retryJob: Job? = null
    private var stopped = false
    private var attempt = 0

    fun start() { stopped = false; connect() }
    fun stop() { stopped = true; retryJob?.cancel(); socket?.close(1000, "app stopped") }

    private fun connect() {
        val request = Request.Builder().url(url.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://").trimEnd('/') + "/v1/ws")
            .header("Authorization", "Bearer $deviceToken")
            .header("X-Kemo-Session", sessionToken)
            .header("X-Kemo-Device-Id", deviceId)
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { attempt = 0 }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { ApiClient.json.decodeFromString<EventDto>(text) }.onSuccess {
                    if (it.type == "ping") webSocket.send("{\"type\":\"pong\"}") else onEvent(it)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // 403/401 = 鉴权失败（会话无效），自动重连无意义，停止重试等待用户重新连接
                if (response?.code == 403 || response?.code == 401) { stop(); return }
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
