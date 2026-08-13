package com.kesepain.kemoapp.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Streaming

interface RestApi {
    @GET("v1/health") suspend fun health(): Response<ResponseBody>
    @GET("v1/conversations") suspend fun conversations(@Query("limit") limit: Int = 50): Response<ResponseBody>
    @GET("v1/conversations/active") suspend fun activeConversation(@Query("client_id") clientId: String): Response<ResponseBody>
    @DELETE("v1/conversations") suspend fun deleteAllConversations(): Response<ResponseBody>
    @GET("v1/conversations/{id}/messages") suspend fun conversationMessages(@Path("id") id: String, @Query("limit") limit: Int = 100): Response<ResponseBody>
    @DELETE("v1/conversations/{id}") suspend fun deleteConversation(
        @Path("id") id: String,
        @Query("client_id") clientId: String = "",
    ): Response<ResponseBody>
    @POST("v1/conversations/{id}/close") suspend fun closeConversation(
        @Path("id") id: String,
        @Query("client_id") clientId: String = "",
    ): Response<ResponseBody>
    @POST("v1/conversations/{id}/compress") suspend fun compressConversation(@Path("id") id: String): Response<ResponseBody>
    @POST("v1/conversations/{id}/undo-last-round") suspend fun undoLastRound(@Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>
    @POST("v1/guidance") suspend fun submitGuidance(@Body body: RequestBody): Response<ResponseBody>
    @POST("v1/runs/{id}/cancel") suspend fun cancelRun(@Path("id") id: String): Response<ResponseBody>
    @GET("v1/task_plans") suspend fun taskPlans(): Response<ResponseBody>
    @POST("v1/task_plans/{id}/{action}") suspend fun taskAction(@Path("id") id: String, @Path("action") action: String): Response<ResponseBody>
    @GET("v1/cron") suspend fun cron(): Response<ResponseBody>
    @POST("v1/cron") suspend fun createCron(@Body body: RequestBody): Response<ResponseBody>
    @PUT("v1/cron/{id}") suspend fun updateCron(@Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>
    @DELETE("v1/cron/{id}") suspend fun deleteCron(@Path("id") id: String): Response<ResponseBody>
    @GET("v1/status") suspend fun status(
        @Query("session_id") sessionId: String = "",
        @Query("client_id") clientId: String = "",
    ): Response<ResponseBody>
    @GET("v1/expands") suspend fun expands(): Response<ResponseBody>
    @GET("v1/expands/data") suspend fun expandsData(): Response<ResponseBody>
    @GET("v1/senses") suspend fun senses(): Response<ResponseBody>
    @PUT("v1/whitelist") suspend fun setWhitelist(@Body body: RequestBody): Response<ResponseBody>
    @GET("v1/files") suspend fun files(
        @Query("scope") scope: String = "download",
        @Query("path") path: String = "",
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<ResponseBody>
    @Multipart @POST("v1/upload") suspend fun uploadFile(@Query("path") path: String = "", @Part file: MultipartBody.Part): Response<ResponseBody>
    @Streaming @GET("v1/files/download") suspend fun downloadFile(@Query("scope") scope: String = "download", @Query("path") path: String): Response<ResponseBody>
    @DELETE("v1/files") suspend fun deleteFile(@Query("scope") scope: String, @Query("path") path: String): Response<ResponseBody>
    @GET("v1/knowledge") suspend fun knowledge(): Response<ResponseBody>
    @GET("v1/knowledge/search") suspend fun knowledgeSearch(@Query("q") query: String): Response<ResponseBody>
    @GET("v1/models") suspend fun models(@Query("refresh") refresh: Boolean = false): Response<ResponseBody>
    @GET("v1/models/capabilities") suspend fun modelCapabilities(@Query("model") model: String, @Query("refresh") refresh: Boolean = false): Response<ResponseBody>
    @PUT("v1/provider/model") suspend fun setModel(@Body body: RequestBody): Response<ResponseBody>
    @GET("v1/config") suspend fun config(): Response<ResponseBody>
    @retrofit2.http.PATCH("v1/config") suspend fun patchConfig(@Body body: RequestBody): Response<ResponseBody>
    @Streaming @GET("v1/avatar") suspend fun avatar(): Response<ResponseBody>
    @GET("v1/version") suspend fun version(): Response<ResponseBody>
}
