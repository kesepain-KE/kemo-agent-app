package com.kesepain.kemoapp.data.remote

import com.kesepain.kemoapp.data.local.AccountConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit

data class ApiBundle(val client: OkHttpClient, val rest: RestApi, val baseUrl: String)

object ApiClient {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun create(account: AccountConfig, secrets: ApiSecrets): ApiBundle {
        val baseUrl = account.baseUrl.trimEnd('/') + "/"
        val client = OkHttpClient.Builder()
            .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("Authorization", "Bearer ${secrets.deviceToken}")
                if (chain.request().header("Accept").isNullOrBlank()) builder.header("Accept", "application/json")
                if (secrets.sessionToken.isNotBlank()) builder.header("X-Kemo-Session", secrets.sessionToken)
                chain.proceed(builder.build())
            }
            .build()
        val rest = Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(retrofit2.converter.scalars.ScalarsConverterFactory.create())
            .build().create(RestApi::class.java)
        return ApiBundle(client, rest, baseUrl)
    }

    fun requestBody(value: Any): okhttp3.RequestBody = when (value) {
        is String -> value.toRequestBody(jsonMediaType)
        else -> json.encodeToString(value.toString()).toRequestBody(jsonMediaType)
    }

    fun chatRequest(bundle: ApiBundle, body: ChatRequestDto): Request = Request.Builder()
        .url(bundle.baseUrl + "v1/chat")
        .post(json.encodeToString(body).toRequestBody(jsonMediaType))
        .header("Accept", "text/event-stream")
        .build()
}
