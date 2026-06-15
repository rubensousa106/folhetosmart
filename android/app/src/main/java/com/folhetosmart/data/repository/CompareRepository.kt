package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiClient
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.ProductDto
import com.folhetosmart.data.api.ProductPriceDto
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
    }
}
