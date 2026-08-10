package com.kesepain.kemoapp.data.remote

import okhttp3.Request

interface ChatApi {
    fun chatRequest(body: ChatRequestDto): Request
}
