package com.folhetosmart.data.api

/**
 * DTOs da API. Os campos são camelCase em Kotlin e mapeiam para snake_case no
 * JSON através do FieldNamingPolicy do Gson (ver [ApiClient]).
 */

// --- Sincronização ---------------------------------------------------------
data class SyncStatusDto(
    val supermarkets: List<SupermarketStatusDto> = emptyList(),
    val allReady: Boolean = false,
    val readyCount: Int = 0,
    val totalCount: Int = 0,
    val lastSync: LastSyncDto? = null,
    // Honestidade da UI (Fix 3): só há "Ver promoções" se houver dados reais.
    val hasCurrentWeekData: Boolean = false,
    val totalProductsThisWeek: Int = 0
)

data class SupermarketStatusDto(
    val name: String,
    val slug: String,
    val flyerAvailable: Boolean,
    val availableSince: String? = null,
    // Estado de sincronização (4 estados): pending | running | success | error
    val syncStatus: String = "pending",
    val productsImported: Int = 0,
    val syncedAt: String? = null,
    val errorMessage: String? = null,
    val syncSource: String? = null,     // site | drive | upload
    val progressMessage: String? = null // ex.: "página 2/4" durante running
)

data class LastSyncDto(
    val finishedAt: String? = null,
    val productsMatched: Int = 0,
    val promotionsFound: Long = 0,
    val avgSavingsPct: Double? = null
)

data class SyncTriggerDto(
    val syncRunId: String,
    val status: String,
    val readyCount: Int,
    val totalCount: Int
)

data class SyncUploadDto(
    val syncRunId: String,
    val status: String
)

data class SyncRunDto(
    val id: String,
    val status: String,                 // "pending" | "running" | "done" | "error"
    val triggeredBy: String? = null,
    val supermarketsReady: Int = 0,
    val supermarketsTotal: Int = 0,
    val productsMatched: Int = 0,
    val productsUnmatched: Int = 0,
    val errorMessage: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null
)

// --- Produtos e preços -----------------------------------------------------
data class PageDto<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val number: Int = 0
)

data class ProductDto(
    val id: String,
    val canonicalName: String,
    val displayName: String,
    val brand: String? = null,
    val category: String? = null,
    val weightGrams: Int? = null
)

data class ProductPriceDto(
    val supermarket: String,
    val supermarketSlug: String,
    val price: Double,
    val originalPrice: Double? = null,
    val isPromotion: Boolean = false,
    val promotionLabel: String? = null,
    val validUntil: String? = null,
    val isBestPrice: Boolean = false
)

// --- Comparação ------------------------------------------------------------
data class CompareRequest(val productIds: List<String>)

data class CompareResultDto(
    val productId: String,
    val displayName: String,
    val bestSupermarket: String? = null,
    val bestPrice: Double? = null,
    val prices: List<ProductPriceDto> = emptyList()
)

// --- Lista de compras otimizada -------------------------------------------
data class OptimizeRequest(val items: List<OptimizeItem>)
data class OptimizeItem(val productId: String, val quantity: Int)

data class OptimizeResponseDto(
    val totalOtimizado: Double = 0.0,
    val poupanca: Double = 0.0,
    val porSupermercado: List<SupermarketBasketDto> = emptyList()
)

data class SupermarketBasketDto(
    val supermarket: String,
    val supermarketSlug: String,
    val subtotal: Double,
    val items: List<BasketItemDto> = emptyList()
)

data class BasketItemDto(
    val productId: String,
    val displayName: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double
)

// --- Promoções -------------------------------------------------------------
data class PromotionDto(
    val productId: String,
    val displayName: String,
    val supermarket: String,
    val supermarketSlug: String,
    val price: Double,
    val originalPrice: Double? = null,
    val promotionLabel: String? = null,
    val savingsPct: Double? = null,
    val validUntil: String? = null
)

// --- Autenticação ----------------------------------------------------------
data class RegisterRequest(val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(
    val token: String,
    val refreshToken: String? = null,
    val email: String,
    val role: String
)

// --- Privacidade (RGPD) ----------------------------------------------------
data class ConsentRequest(
    val version: String,
    val notificationsAccepted: Boolean
)

// --- Perfil ------------------------------------------------------------------
data class UserMeDto(
    val id: String,
    val email: String,
    val role: String,
    val district: String? = null,
    val city: String? = null
)

data class UpdateMeRequest(
    val district: String? = null,
    val city: String? = null
)

// --- Alertas ---------------------------------------------------------------
data class AlertRequest(
    val productId: String,
    val targetPrice: Double? = null,
    val anyPromotion: Boolean = false
)

data class AlertDto(
    val id: String,
    val productId: String,
    val productDisplayName: String,
    val targetPrice: Double? = null,
    val anyPromotion: Boolean = false,
    val active: Boolean = true,
    val createdAt: String? = null
)

// --- Administração (painel só-ADMIN) ---------------------------------------
data class AdminUploadResponseDto(
    val syncRunId: String,
    val filename: String,
    val driveFileId: String? = null,
    val status: String                  // "processing"
)

data class AdminFlyersStatusDto(
    val week: String,                   // "DD-MM-YYYY - DD-MM-YYYY"
    val supermarkets: List<AdminFlyerStatusDto> = emptyList()
)

data class AdminFlyerStatusDto(
    val name: String,
    val slug: String,
    val hasFlyer: Boolean = false,
    val driveFilename: String? = null,
    val productsImported: Int = 0,
    val syncedAt: String? = null
)

// Pipeline novo (2 passos): Drive (memória) -> Claude (PDF nativo).
data class AdminUploadToDriveDto(
    val driveFileId: String? = null,
    val filename: String? = null
)

data class ProcessFlyerRequest(
    val driveFileId: String,
    val supermarketSlug: String,
    val validFrom: String,
    val validUntil: String
)

data class ProcessFlyerResponseDto(
    val productsImported: Int = 0,
    val status: String = "error"        // "success" | "error"
)
