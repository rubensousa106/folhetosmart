package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiClient
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.SyncRunDto
import com.folhetosmart.data.api.SyncStatusDto
import com.folhetosmart.data.api.SyncTriggerDto
import com.folhetosmart.data.api.SyncUploadDto
import com.folhetosmart.data.local.CacheDao
import com.folhetosmart.data.local.CacheEntry
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Resultado que indica se veio da rede ou da cache offline. */
data class CachedData<T>(val data: T, val fromCache: Boolean)

class SyncRepository(
    private val api: ApiService,
    private val cache: CacheDao
) {
    private val gson = ApiClient.gson

    /** Estado dos folhetos; cai para a cache Room se não houver rede. */
    suspend fun status(): CachedData<SyncStatusDto> {
        return try {
            val fresh = api.syncStatus()
            cache.put(CacheEntry(KEY_STATUS, gson.toJson(fresh), System.currentTimeMillis()))
            CachedData(fresh, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = cache.get(KEY_STATUS) ?: throw e
            CachedData(gson.fromJson(cached.json, SyncStatusDto::class.java), fromCache = true)
        }
    }

    suspend fun trigger(): SyncTriggerDto = api.syncTrigger()

    suspend fun run(runId: String): SyncRunDto = api.syncRun(runId)

    /** Upload manual de folheto em PDF (Fix 3). */
    suspend fun uploadPdf(slug: String, pdfBytes: ByteArray): SyncUploadDto {
        val body = pdfBytes.toRequestBody("application/pdf".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "$slug.pdf", body)
        return api.uploadFlyerPdf(slug, part)
    }

    private companion object {
        const val KEY_STATUS = "sync_status"
    }
}
