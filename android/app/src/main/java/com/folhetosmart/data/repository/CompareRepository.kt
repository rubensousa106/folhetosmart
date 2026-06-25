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
        val merged = urls.flatMap { url ->
            try {
                api.downloadFeed(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()  // um feed em falta não trava os outros
            }
        }
        // Marca estes feeds como sincronizados — serve para detetar futuros feeds
        // novos no servidor (auto-sincronização + alerta "novos produtos").
        markFeedsSynced(feedKeys(urls))
        return merged
    }

    /**
     * Há no servidor algum feed DIFERENTE do último sincronizado? (= produtos
     * novos publicados que a app ainda não trouxe). Compara o conjunto de nomes
     * de ficheiro dos feeds ativos com o conjunto guardado na última sincronização.
     */
    suspend fun hasNewerFeed(): Boolean {
        val server = try {
            feedKeys(api.getFeeds())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false  // sem ligação: não inventamos novidades
        }
        return server.isNotEmpty() && server != syncedFeedKeys()
    }

    /** Nome de ficheiro de cada feed (ex.: "produtos_22-06-2026_28-06-2026.json"),
     *  ignorando a query da assinatura do R2. Ordenado para comparar como conjunto. */
    private fun feedKeys(urls: List<String>): List<String> =
        urls.map { it.substringBefore('?').substringAfterLast('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    private suspend fun syncedFeedKeys(): List<String> {
        val json = cache.get(KEY_SYNCED_FEEDS)?.json ?: return emptyList()
        return try {
            gson.fromJson(json, Array<String>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun markFeedsSynced(keys: List<String>) {
        cache.put(CacheEntry(KEY_SYNCED_FEEDS, gson.toJson(keys), System.currentTimeMillis()))
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
        const val KEY_SYNCED_FEEDS = "synced_feed_keys"  // feeds da última sincronização
        const val FRESH_MS = 7 * 24 * 60 * 1000L  // 7 dias — descarrega 1x e usa offline
    }
}
