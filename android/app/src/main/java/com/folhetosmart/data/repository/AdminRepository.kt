package com.folhetosmart.data.repository

import com.folhetosmart.data.api.AdminFlyersStatusDto
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.SyncRunDto
import com.folhetosmart.data.api.SyncStatusDto
import com.folhetosmart.data.api.SyncTriggerDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Operações do painel de administração (só ADMIN). O JWT da sessão é injetado
 * automaticamente pelo [ApiClient]; o backend valida o papel (403 caso falhe).
 * O upload de folhetos vai direto app→Cloudflare R2 (link assinado pelo backend).
 */
class AdminRepository(private val api: ApiService) {

    /** Estado dos folhetos da semana atual. */
    suspend fun flyersStatus(): AdminFlyersStatusDto = api.adminFlyersStatus()

    /** Força a sincronização automática. */
    suspend fun trigger(): SyncTriggerDto = api.adminTrigger()

    /** Estado por supermercado (sync_status running/success/error) para o progresso. */
    suspend fun syncStatus(): SyncStatusDto = api.syncStatus()

    /** Acompanha o progresso de um sync_run. */
    suspend fun run(runId: String): SyncRunDto = api.syncRun(runId)

    /** Pede ao backend um link assinado para PUT do PDF no R2 → (url, filename). */
    suspend fun flyerUploadUrl(
        supermarket: String,
        validFrom: String,
        validUntil: String
    ): Pair<String, String> {
        val r = api.flyerUploadUrl(supermarket, validFrom, validUntil)
        val url = r["url"] ?: throw IllegalStateException("Sem URL de upload do R2.")
        return url to (r["filename"] ?: "")
    }

    /** Faz PUT do PDF diretamente para o R2 (o ficheiro não passa pelo Render). */
    suspend fun uploadFlyerToR2(url: String, pdfBytes: ByteArray) {
        val body = pdfBytes.toRequestBody("application/pdf".toMediaType())
        val resp = api.uploadFileToUrl(url, body)
        if (!resp.isSuccessful) {
            throw IllegalStateException("O R2 recusou o upload (${resp.code()}).")
        }
    }
}
