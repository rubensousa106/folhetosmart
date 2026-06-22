package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiClient
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.ProductDto
import com.folhetosmart.data.api.ProductPriceDto
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

    suspend fun search(query: String): List<ProductDto> =
        api.searchProducts(search = query).content

    /**
     * Todos os produtos de todos os supermercados (modelo simples dos folhetos),
     * com cache local: serve do disco se a cópia for fresca (< 12h) — poupa
     * pedidos e funciona sem internet — e recorre à última cópia guardada se o
     * servidor estiver a dormir / offline.
     */
    suspend fun allOfferings(force: Boolean = false): List<FlyerOfferingDto> {
        val cached = cache.get(KEY_ALL)
        if (!force && cached != null && System.currentTimeMillis() - cached.updatedAt < FRESH_MS) {
            return parseOfferings(cached.json)
        }
        return try {
            val fresh = api.getAllFlyerProducts()
            cache.put(CacheEntry(KEY_ALL, gson.toJson(fresh), System.currentTimeMillis()))
            fresh
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached?.let { parseOfferings(it.json) } ?: throw e
        }
    }

    private fun parseOfferings(json: String): List<FlyerOfferingDto> {
        val type = object : TypeToken<List<FlyerOfferingDto>>() {}.type
        return gson.fromJson(json, type)
    }

    /**
     * Preços atuais de um produto. A última consulta de cada produto fica em
     * cache para a "última comparação" funcionar offline (regra 5).
     */
    suspend fun prices(productId: String): CachedData<List<ProductPriceDto>> {
        val key = "$KEY_PRICES_PREFIX$productId"
        return try {
            val fresh = api.productPrices(productId)
            cache.put(CacheEntry(key, gson.toJson(fresh), System.currentTimeMillis()))
            CachedData(fresh, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = cache.get(key) ?: throw e
            val type = object : TypeToken<List<ProductPriceDto>>() {}.type
            CachedData(gson.fromJson(cached.json, type), fromCache = true)
        }
    }

    private companion object {
        const val KEY_PRICES_PREFIX = "prices_"
        const val KEY_ALL = "all_offerings"
        const val FRESH_MS = 12 * 60 * 60 * 1000L  // 12h — descarrega 1x e usa offline
    }
}
