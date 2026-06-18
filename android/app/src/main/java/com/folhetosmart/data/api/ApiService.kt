package com.folhetosmart.data.api

import com.folhetosmart.data.models.SupermarketResponse
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

/** Endpoints REST do backend FolhetoSmart. */
interface ApiService {

    // --- Sincronização ---
    @GET("api/v1/sync/status")
    suspend fun syncStatus(): SyncStatusDto

    @POST("api/v1/sync/trigger")
    suspend fun syncTrigger(): SyncTriggerDto

    @GET("api/v1/sync/runs/{id}")
    suspend fun syncRun(@Path("id") runId: String): SyncRunDto

    /** Upload manual de folheto em PDF (Fix 3). */
    @Multipart
    @POST("api/v1/sync/upload/{slug}")
    suspend fun uploadFlyerPdf(
        @Path("slug") slug: String,
        @Part file: MultipartBody.Part
    ): SyncUploadDto

    // --- Produtos / preços ---
    @GET("api/v1/products")
    suspend fun searchProducts(
        @Query("search") search: String?,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PageDto<ProductDto>

    /** Produtos da semana atual (leitura para a cache local — Fix 5). */
    @GET("api/v1/products")
    suspend fun weekProducts(
        @Query("week") week: String = "current",
        @Query("size") size: Int = 200
    ): PageDto<ProductDto>

    @GET("api/v1/products/{id}/prices")
    suspend fun productPrices(@Path("id") productId: String): List<ProductPriceDto>

    // ============================================================
    // 🆕 NOVO - GET /api/v1/products/latest
    // ============================================================
   
    @GET("api/v1/products/latest")
    suspend fun getLatestProducts(
        @Query("supermarket") supermarket: String
    ): retrofit2.Response<SupermarketResponse>

    // --- Comparação ---
    @POST("api/v1/compare")
    suspend fun compare(@Body request: CompareRequest): List<CompareResultDto>

    // --- Lista de compras ---
    @POST("api/v1/shopping-list/optimize")
    suspend fun optimize(@Body request: OptimizeRequest): OptimizeResponseDto

    // --- Promoções ---
    @GET("api/v1/promotions")
    suspend fun promotions(@Query("supermarket") supermarket: String? = null): List<PromotionDto>

    // --- Perfil (requer JWT) ---
    @GET("api/v1/users/me")
    suspend fun me(@Header("Authorization") bearer: String): UserMeDto

    @PUT("api/v1/users/me")
    suspend fun updateMe(
        @Header("Authorization") bearer: String,
        @Body request: UpdateMeRequest
    ): UserMeDto

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
    /** Upload de um folheto PDF -> Google Drive + extração com IA. */
    @Multipart
    @POST("api/v1/admin/upload-flyer")
    suspend fun adminUploadFlyer(
        @Part("supermarket_slug") supermarketSlug: RequestBody,
        @Part("valid_from") validFrom: RequestBody,
        @Part("valid_until") validUntil: RequestBody,
        @Part file: MultipartBody.Part
    ): AdminUploadResponseDto

    @GET("api/v1/admin/flyers/status")
    suspend fun adminFlyersStatus(): AdminFlyersStatusDto

    @POST("api/v1/admin/sync/trigger")
    suspend fun adminTrigger(): SyncTriggerDto

    // Pipeline novo (2 passos): Drive (memória) -> Claude (PDF nativo).
    @Multipart
    @Headers("X-Long-Timeout: 1")
    @POST("api/v1/admin/upload-to-drive")
    suspend fun adminUploadToDrive(
        @Part("supermarket_slug") supermarketSlug: RequestBody,
        @Part("valid_from") validFrom: RequestBody,
        @Part("valid_until") validUntil: RequestBody,
        @Part file: MultipartBody.Part
    ): AdminUploadToDriveDto

    /** Síncrono (~1-2 min) — a extração com IA corre aqui; usa timeout longo. */
    @Headers("X-Long-Timeout: 1")
    @POST("api/v1/admin/process-flyer")
    suspend fun adminProcessFlyer(@Body request: ProcessFlyerRequest): ProcessFlyerResponseDto
}
