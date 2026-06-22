package com.folhetosmart.features.admin

import com.folhetosmart.data.api.AdminFlyerStatusDto
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** PDF escolhido pelo utilizador (mantido em memória até ao envio). */
class PickedPdf(val label: String, val bytes: ByteArray)

/** Linha de progresso por supermercado durante "Forçar sincronização". */
data class MarketLine(
    val name: String,
    val label: String,
    val done: Boolean,
    val failed: Boolean
)

/** Progresso detalhado do processamento — só existe no ecrã Admin. */
data class SyncProgress(
    val doneCount: Int,
    val totalCount: Int,
    val etaSeconds: Int,
    val lines: List<MarketLine>
)

/** Tracker no Admin: estado do feed por supermercado (✓ com produtos / ✗ sem). */
data class FeedStoreStatus(val name: String, val hasProducts: Boolean, val count: Int)

/** Fases da máquina de estados do upload (Fix 5). */
sealed interface UploadPhase {
    /** Sem PDF — ação: "Selecionar PDF". */
    data object Idle : UploadPhase

    /** PDF escolhido — botão "Carregar folheto" ativo. */
    data object Selected : UploadPhase

    /** A enviar o ficheiro para o Drive. */
    data object Uploading : UploadPhase

    /** A correr a extração com IA (1–2 min). */
    data object Processing : UploadPhase

    /** Concluído — "✅ N produtos importados". */
    data class Done(val productsImported: Int) : UploadPhase

    /** Falhou — mensagem + "Tentar novamente". */
    data class Error(val message: String) : UploadPhase
}

/**
 * Estado completo do ecrã Admin: lista de estado dos folhetos + formulário de
 * upload (supermercado, semana de validade, PDF) + fase do upload.
 */
data class AdminUiState(
    val adminEmail: String? = null,
    val statusLoading: Boolean = false,
    val statusError: String? = null,
    val week: String = "",
    val supermarkets: List<AdminFlyerStatusDto> = emptyList(),

    // Tracker baseado no feed real (produtos por supermercado esta semana).
    val feedStores: List<FeedStoreStatus> = emptyList(),
    val feedLoading: Boolean = false,
    val feedError: String? = null,

    // Formulário
    val selectedSlug: String? = null,
    val selectedSupermarket: String? = null,   // nome do supermercado (para o upload R2)
    val weekStart: LocalDate = currentMonday(),
    val picked: PickedPdf? = null,

    // Máquina de estados do upload + "Forçar sincronização"
    val phase: UploadPhase = UploadPhase.Idle,
    val syncing: Boolean = false,
    val syncProgress: SyncProgress? = null
) {
    val validFrom: String get() = weekStart.format(DATE_FMT)
    val validUntil: String get() = weekStart.plusDays(6).format(DATE_FMT)
    val weekLabel: String get() = "$validFrom - $validUntil"

    /** Pré-visualização do nome com que o folheto fica guardado no R2. */
    val previewFilename: String?
        get() = selectedSupermarket?.let { "$it $weekLabel.pdf" }

    val isBusy: Boolean
        get() = phase is UploadPhase.Uploading || phase is UploadPhase.Processing

    val canUpload: Boolean
        get() = selectedSupermarket != null && picked != null && !isBusy

    companion object {
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val UPLOAD_STORES = listOf("Continente", "Pingo Doce", "Lidl", "Aldi", "Intermarché")
        fun currentMonday(): LocalDate =
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
