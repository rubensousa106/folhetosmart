package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiClient
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.OptimizeItem
import com.folhetosmart.data.api.OptimizeRequest
import com.folhetosmart.data.api.OptimizeResponseDto
import com.folhetosmart.data.api.ProductDto
import com.folhetosmart.data.local.CacheDao
import com.folhetosmart.data.local.CacheEntry
import com.folhetosmart.data.local.ShoppingDao
import com.folhetosmart.data.local.ShoppingItemEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class ShoppingRepository(
    private val api: ApiService,
    private val shoppingDao: ShoppingDao,
    private val cache: CacheDao
) {
    private val gson = ApiClient.gson

    /** Lista de compras persistida localmente (Room). */
    val items: Flow<List<ShoppingItemEntity>> = shoppingDao.observeItems()

    suspend fun addProduct(product: ProductDto) =
        shoppingDao.upsert(ShoppingItemEntity(product.id, product.displayName, 1))

    suspend fun setQuantity(item: ShoppingItemEntity, quantity: Int) {
        if (quantity <= 0) shoppingDao.delete(item.productId)
        else shoppingDao.upsert(item.copy(quantity = quantity))
    }

    suspend fun remove(productId: String) = shoppingDao.delete(productId)

    suspend fun searchProducts(query: String): List<ProductDto> =
        api.searchProducts(search = query).content

    /** Otimiza a lista; a última otimização fica em cache para uso offline. */
    suspend fun optimize(items: List<ShoppingItemEntity>): CachedData<OptimizeResponseDto> {
        val request = OptimizeRequest(items.map { OptimizeItem(it.productId, it.quantity) })
        return try {
            val fresh = api.optimize(request)
            cache.put(CacheEntry(KEY_LAST_OPTIMIZE, gson.toJson(fresh), System.currentTimeMillis()))
            CachedData(fresh, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = cache.get(KEY_LAST_OPTIMIZE) ?: throw e
            CachedData(
                gson.fromJson(cached.json, OptimizeResponseDto::class.java),
                fromCache = true
            )
        }
    }

    private companion object {
        const val KEY_LAST_OPTIMIZE = "last_optimize"
    }
}
