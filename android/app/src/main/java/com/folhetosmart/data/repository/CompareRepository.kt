package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiClient
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.models.FlyerOfferingDto
import com.folhetosmart.data.local.CacheDao
import com.folhetosmart.data.local.CacheEntry
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException

class CompareRepository(
    private val api: ApiService,
    private val cache: CacheDao
) {
    private val gson = ApiClient.gson

    /**
     * Todas as ofertas VÁLIDAS de todos os supermercados. MULTI-FEED: descarrega
     * a lista de feeds ativos (GET /products/feeds — ex.: supermercados principais
     * + Aldi com datas próprias), funde-os e esconde os expirados (data de fim da
     * validade < hoje). Cache local (12h) + recurso à última cópia se offline.
     */
    suspend fun allOfferings(force: Boolean = false): List<FlyerOfferingDto> {
        val cached = cache.get(KEY_ALL)
        if (!force && cached != null && System.currentTimeMillis() - cached.updatedAt < FRESH_MS) {
            return parseOfferings(cached.json)
        }
        return try {
            val merged = fetchAndMerge()
            cache.put(CacheEntry(KEY_ALL, gson.toJson(merged), System.currentTimeMillis()))
            merged.filterNot { expirou(it.validade) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached?.let { parseOfferings(it.json) } ?: throw e
        }
    }

    /** Descarrega e funde todos os feeds ativos; recorre ao /all se /feeds falhar. */
    private suspend fun fetchAndMerge(): List<FlyerOfferingDto> {
        val urls = try {
            api.getFeeds()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (urls.isEmpty()) {
            return api.getAllFlyerProducts()  // compat: endpoint único (302 -> R2)
        }
        return urls.flatMap { url ->
            try {
                api.downloadFeed(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()  // um feed em falta não trava os outros
            }
        }
    }

    private fun parseOfferings(json: String): List<FlyerOfferingDto> {
        val type = object : TypeToken<List<FlyerOfferingDto>>() {}.type
        val list: List<FlyerOfferingDto> = gson.fromJson(json, type)
        return list.filterNot { expirou(it.validade) }
    }

    /** True se a validade já terminou (data de fim anterior a hoje). "…a DD/MM/AAAA". */
    private fun expirou(validade: String?): Boolean {
        val fim = validade?.substringAfterLast(" a ", "")?.trim().orEmpty()
        if (fim.isEmpty()) return false
        return try {
            val d = java.time.LocalDate.parse(
                fim, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            d.isBefore(java.time.LocalDate.now())
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val KEY_ALL = "all_offerings"
        const val FRESH_MS = 12 * 60 * 60 * 1000L  // 12h — descarrega 1x e usa offline
    }
}
