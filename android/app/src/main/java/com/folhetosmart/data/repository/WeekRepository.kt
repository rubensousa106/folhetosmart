package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiClient
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.ProductDto
import com.folhetosmart.data.local.AppPrefs
import com.folhetosmart.data.local.CacheDao
import com.folhetosmart.data.local.CacheEntry
import com.google.gson.reflect.TypeToken

/**
 * Cache local dos produtos da semana (Fix 5 — offline-first).
 *
 * Sincroniza da BD apenas quando a semana muda; caso contrário usa a cache
 * Room. Não há processamento aqui — é só leitura de dados já prontos no
 * servidor (Fix 3).
 */
class WeekRepository(
    private val api: ApiService,
    private val cache: CacheDao,
    private val prefs: AppPrefs
) {
    private val gson = ApiClient.gson
    private val listType = object : TypeToken<List<ProductDto>>() {}.type

    /** True se ainda não sincronizou esta semana. */
    fun needsSync(): Boolean = prefs.needsWeeklySync()

    /**
     * Sincroniza os produtos da semana só quando é preciso ir ao servidor:
     * nunca sincronizou, a cache tem mais de 1 hora, ou foi forçado (botão
     * "Atualizar"). Caso contrário usa a cache Room (instantâneo). Em falha de
     * rede, cai sempre para a cache.
     */
    suspend fun syncWeekProducts(force: Boolean = false): List<ProductDto> {
        if (!force && isCacheFresh()) {
            return cachedWeekProducts()
        }
        return try {
            val products = api.weekProducts().content
            cache.put(CacheEntry(KEY_WEEK, gson.toJson(products), System.currentTimeMillis()))
            prefs.lastSyncWeek = AppPrefs.currentWeekKey()
            products
        } catch (e: Exception) {
            cachedWeekProducts()   // offline -> última semana sincronizada
        }
    }

    /** Cache fresca = produtos desta semana guardados há menos de 1 hora. */
    private suspend fun isCacheFresh(): Boolean {
        val entry = cache.get(KEY_WEEK) ?: return false
        val sameWeek = prefs.lastSyncWeek == AppPrefs.currentWeekKey()
        val ageMs = System.currentTimeMillis() - entry.updatedAt
        return sameWeek && ageMs < FRESH_WINDOW_MS
    }

    suspend fun cachedWeekProducts(): List<ProductDto> {
        val entry = cache.get(KEY_WEEK) ?: return emptyList()
        return gson.fromJson(entry.json, listType)
    }

    private companion object {
        const val KEY_WEEK = "week_products"
        const val FRESH_WINDOW_MS = 60 * 60 * 1000L   // 1 hora
    }
}
