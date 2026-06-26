package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.ChangeEmailRequest
import com.folhetosmart.data.api.ChangePasswordRequest
import com.folhetosmart.data.api.UpdateMeRequest
import com.folhetosmart.data.api.UserMeDto
import com.folhetosmart.data.local.TokenStore

/** Perfil do utilizador — leitura e edição (nome, email, password, distrito, cidade). */
class UserRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore
) {
    /** Email guardado no token (local, estável e disponível offline) — usado, por
     *  exemplo, para semear o avatar sem depender de uma chamada de rede. */
    val email: String? get() = tokenStore.email

    suspend fun me(): UserMeDto = api.me(requireBearer())

    /** Atualiza nome + distrito + cidade. */
    suspend fun updateProfile(name: String?, district: String?, city: String?): UserMeDto =
        api.updateMe(requireBearer(), UpdateMeRequest(name, district, city))

    /** Passo 2 do registo — guarda só a localização (o nome ainda não existe). */
    suspend fun updateLocation(district: String?, city: String?): UserMeDto =
        api.updateMe(requireBearer(), UpdateMeRequest(district = district, city = city))

    /** Troca a palavra-passe (o backend exige a atual). */
    suspend fun changePassword(currentPassword: String, newPassword: String) =
        api.changePassword(requireBearer(), ChangePasswordRequest(currentPassword, newPassword))

    /**
     * Troca o email. Como o email é o "subject" do JWT, o backend reemite os
     * tokens; guardamo-los para a sessão continuar sem novo login.
     */
    suspend fun changeEmail(currentPassword: String, newEmail: String) {
        val resp = api.changeEmail(requireBearer(), ChangeEmailRequest(currentPassword, newEmail))
        tokenStore.token = resp.token
        resp.refreshToken?.let { tokenStore.refreshToken = it }
        tokenStore.email = resp.email
    }

    private fun requireBearer(): String =
        tokenStore.bearer ?: error("Sessão expirada. Inicia sessão novamente.")
}
