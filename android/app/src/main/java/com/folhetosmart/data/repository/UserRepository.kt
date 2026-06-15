package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.UpdateMeRequest
import com.folhetosmart.data.api.UserMeDto
import com.folhetosmart.data.local.TokenStore

/** Perfil do utilizador (inclui distrito/cidade para o folheto Aldi). */
class UserRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore
) {
    suspend fun me(): UserMeDto = api.me(requireBearer())

    suspend fun updateLocation(district: String?, city: String?): UserMeDto =
        api.updateMe(requireBearer(), UpdateMeRequest(district, city))

    private fun requireBearer(): String =
        tokenStore.bearer ?: error("Sessão expirada. Inicia sessão novamente.")
}
