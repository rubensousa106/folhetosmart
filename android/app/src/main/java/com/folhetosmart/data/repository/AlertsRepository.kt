package com.folhetosmart.data.repository

import com.folhetosmart.data.api.AlertDto
import com.folhetosmart.data.api.AlertRequest
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.AuthResponse
import com.folhetosmart.data.api.ForgotPasswordRequest
import com.folhetosmart.data.api.LoginRequest
import com.folhetosmart.data.api.RegisterRequest
import com.folhetosmart.data.local.TokenStore

/** Alertas de preço — requer sessão (JWT). */
class AlertsRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore
) {
    val isLoggedIn: Boolean get() = tokenStore.token != null
    val email: String? get() = tokenStore.email

    suspend fun login(email: String, password: String): AuthResponse =
        api.login(LoginRequest(email, password)).also(::storeSession)

    suspend fun register(email: String, password: String): AuthResponse =
        api.register(RegisterRequest(email, password)).also(::storeSession)

    /** Recuperação: pede ao backend para enviar uma palavra-passe temporária por email. */
    suspend fun forgotPassword(email: String) =
        api.forgotPassword(ForgotPasswordRequest(email))

    fun logout() = tokenStore.clear()

    suspend fun list(): List<AlertDto> = api.listAlerts(requireBearer())

    suspend fun create(productId: String, targetPrice: Double?, anyPromotion: Boolean): AlertDto =
        api.createAlert(requireBearer(), AlertRequest(productId, targetPrice, anyPromotion))

    suspend fun delete(alertId: String) = api.deleteAlert(requireBearer(), alertId)

    private fun storeSession(auth: AuthResponse) {
        tokenStore.token = auth.token
        tokenStore.refreshToken = auth.refreshToken
        tokenStore.email = auth.email
    }

    private fun requireBearer(): String =
        tokenStore.bearer ?: error("Sessão expirada. Inicia sessão novamente.")
}
