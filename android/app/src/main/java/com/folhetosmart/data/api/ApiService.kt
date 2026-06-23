package com.folhetosmart.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/** Endpoints REST do backend FolhetoSmart. */
interface ApiService {

    // --- Sincronização (leitura do estado; processamento corre fora do backend) ---
    @GET("api/v1/sync/status")
    suspend fun syncStatus(): SyncStatusDto

    @GET("api/v1/sync/runs/{id}")
    suspend fun syncRun(@Path("id") runId: String): SyncRunDto

    // --- Produtos dos folhetos ---
    // GET /api/v1/products/all — todos os produtos de todos os supermercados.
    @GET("api/v1/products/all")
    suspend fun getAllFlyerProducts(): List<com.folhetosmart.data.models.FlyerOfferingDto>

    // Upload de folheto para o R2: pede o link assinado e faz PUT direto ao R2.
    @POST("api/v1/admin/flyer-upload-url")
    suspend fun flyerUploadUrl(
        @Query("supermarket") supermarket: String,
        @Query("valid_from") validFrom: String,
        @Query("valid_until") validUntil: String
    ): Map<String, String>

    @PUT
    suspend fun uploadFileToUrl(
        @Url url: String,
        @Body body: RequestBody
    ): retrofit2.Response<okhttp3.ResponseBody?>

    // --- Perfil (requer JWT) ---
    @GET("api/v1/users/me")
    suspend fun me(@Header("Authorization") bearer: String): UserMeDto

    @PUT("api/v1/users/me")
    suspend fun updateMe(
        @Header("Authorization") bearer: String,
        @Body request: UpdateMeRequest
    ): UserMeDto

    @PUT("api/v1/users/me/password")
    suspend fun changePassword(
        @Header("Authorization") bearer: String,
        @Body request: ChangePasswordRequest
    )

    @PUT("api/v1/users/me/email")
    suspend fun changeEmail(
        @Header("Authorization") bearer: String,
        @Body request: ChangeEmailRequest
    ): AuthResponse

    // --- Autenticação ---
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    // --- Privacidade RGPD (requerem JWT) ---
    @GET("api/v1/privacy/my-data")
    suspend fun exportMyData(@Header("Authorization") bearer: String): okhttp3.ResponseBody

    @DELETE("api/v1/privacy/my-account")
    suspend fun deleteMyAccount(@Header("Authorization") bearer: String)

    @POST("api/v1/privacy/consent")
    suspend fun registerConsent(
        @Header("Authorization") bearer: String,
        @Body request: ConsentRequest
    ): okhttp3.ResponseBody

    // --- Alertas (requerem JWT) ---
    @GET("api/v1/alerts")
    suspend fun listAlerts(@Header("Authorization") bearer: String): List<AlertDto>

    @POST("api/v1/alerts")
    suspend fun createAlert(
        @Header("Authorization") bearer: String,
        @Body request: AlertRequest
    ): AlertDto

    @DELETE("api/v1/alerts/{id}")
    suspend fun deleteAlert(
        @Header("Authorization") bearer: String,
        @Path("id") alertId: String
    )

    // --- Administração (só ADMIN; 403 para outros papéis) ---
    @GET("api/v1/admin/flyers/status")
    suspend fun adminFlyersStatus(): AdminFlyersStatusDto

    @POST("api/v1/admin/sync/trigger")
    suspend fun adminTrigger(): SyncTriggerDto
}
