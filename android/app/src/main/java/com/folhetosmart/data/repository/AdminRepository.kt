package com.folhetosmart.data.repository

import com.folhetosmart.data.api.AdminFlyersStatusDto
import com.folhetosmart.data.api.AdminUploadResponseDto
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.SyncRunDto
import com.folhetosmart.data.api.SyncStatusDto
import com.folhetosmart.data.api.SyncTriggerDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Operações do painel de administração (só ADMIN). O JWT da sessão é injetado
 * automaticamente pelo [ApiClient]; o backend valida o papel (403 caso falhe).
 */
class AdminRepository(private val api: ApiService) {

    /** Estado dos folhetos da semana atual. */
    suspend fun flyersStatus(): AdminFlyersStatusDto = api.adminFlyersStatus()

    /** Força a sincronização automática (site -> Drive -> IA). */
    suspend fun trigger(): SyncTriggerDto = api.adminTrigger()

    /** Estado por supermercado (sync_status running/success/error) para o progresso. */
    suspend fun syncStatus(): SyncStatusDto = api.syncStatus()

    /** Acompanha o progresso de um sync_run (reutiliza o endpoint público). */
    suspend fun run(runId: String): SyncRunDto = api.syncRun(runId)

    /**
     * Upload de um folheto PDF. O backend valida o PDF, guarda-o no Google Drive
     * (substituindo se já existir, sem duplicar) e dispara a extração com IA.
     *
     * @param validFrom / [validUntil] no formato DD-MM-AAAA.
     */
    suspend fun uploadFlyer(
        slug: String,
        validFrom: String,
        validUntil: String,
        pdfBytes: ByteArray
    ): AdminUploadResponseDto {
        val body = pdfBytes.toRequestBody("application/pdf".toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", "$slug.pdf", body)
        return api.adminUploadFlyer(
            supermarketSlug = slug.toTextPart(),
            validFrom = validFrom.toTextPart(),
            validUntil = validUntil.toTextPart(),
            file = filePart
        )
    }

    private fun String.toTextPart(): RequestBody =
        toRequestBody("text/plain".toMediaType())
}
