package com.folhetosmart.features.admin

import com.folhetosmart.data.api.AdminFlyerStatusDto
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** PDF escolhido pelo utilizador (mantido em memória até ao envio). */
class PickedPdf(val label: String, val bytes: ByteArray)

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

    // Formulário
    val selectedSlug: String? = null,
    val weekStart: LocalDate = currentMonday(),
    val picked: PickedPdf? = null,

    // Máquina de estados do upload + "Forçar sincronização"
    val phase: UploadPhase = UploadPhase.Idle,
    val syncing: Boolean = false
) {
    val validFrom: String get() = weekStart.format(DATE_FMT)
    val validUntil: String get() = weekStart.plusDays(6).format(DATE_FMT)
    val weekLabel: String get() = "$validFrom - $validUntil"

    private val selectedName: String?
        get() = supermarkets.firstOrNull { it.slug == selectedSlug }?.name

    /** Pré-visualização do nome com que o folheto fica guardado no Drive. */
    val previewFilename: String?
        get() = selectedName?.let { "$it $weekLabel.pdf" }

    val isBusy: Boolean
        get() = phase is UploadPhase.Uploading || phase is UploadPhase.Processing

    val canUpload: Boolean
        get() = selectedSlug != null && picked != null && !isBusy

    companion object {
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        fun currentMonday(): LocalDate =
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
