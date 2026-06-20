package com.folhetosmart

import android.content.Context
import androidx.room.Room
import com.folhetosmart.data.api.ApiClient
import com.folhetosmart.data.local.AppPrefs
import com.folhetosmart.data.local.FolhetoDatabase
import com.folhetosmart.data.local.TokenStore
import com.folhetosmart.data.repository.AdminRepository
import com.folhetosmart.data.repository.AlertsRepository
import com.folhetosmart.data.repository.CompareRepository
import com.folhetosmart.data.repository.PrivacyRepository
import com.folhetosmart.data.repository.ShoppingRepository
import com.folhetosmart.data.repository.SyncRepository
import com.folhetosmart.data.repository.UserRepository
import com.folhetosmart.data.repository.WeekRepository

/** Injeção de dependências manual (suficiente para a dimensão da app). */
class AppContainer(context: Context) {

    val tokenStore = TokenStore(context.applicationContext)
    val appPrefs = AppPrefs(context.applicationContext)

    // O JWT da sessão é injetado em todos os pedidos.
    private val api = ApiClient.create { tokenStore.bearer }

    // Exposto para ViewModels que falam diretamente com a API (ex.: ProductViewModel).
    val apiService get() = api

    private val database = Room.databaseBuilder(
        context.applicationContext,
        FolhetoDatabase::class.java,
        "folhetosmart.db"
    ).build()

    val syncRepository = SyncRepository(api, database.cacheDao())
    val adminRepository = AdminRepository(api)
    val compareRepository = CompareRepository(api, database.cacheDao())
    val shoppingRepository = ShoppingRepository(api, database.shoppingDao(), database.cacheDao())
    val alertsRepository = AlertsRepository(api, tokenStore)
    val privacyRepository = PrivacyRepository(api, tokenStore, database.shoppingDao())
    val userRepository = UserRepository(api, tokenStore)
    val weekRepository = WeekRepository(api, database.cacheDao(), appPrefs)
}
