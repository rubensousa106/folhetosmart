package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.ConsentRequest
import com.folhetosmart.data.local.ShoppingDao
import com.folhetosmart.data.local.TokenStore

/** Direitos RGPD: exportação de dados, eliminação de conta e consentimento. */
class PrivacyRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    private val shoppingDao: ShoppingDao
) {
    /** Versão atual dos Termos/Política aceites no onboarding. */
    val termsVersion: String get() = TERMS_VERSION

    val isLoggedIn: Boolean get() = tokenStore.token != null

    /** Exporta todos os dados do utilizador como JSON (string). */
    suspend fun exportMyData(): String =
        api.exportMyData(requireBearer()).string()

    /**
     * Elimina a conta e todos os dados no servidor (irreversível) e limpa
     * os dados locais (sessão + lista de compras), cumprindo a promessa do
     * diálogo de confirmação.
     */
    suspend fun deleteMyAccount() {
        api.deleteMyAccount(requireBearer())
        shoppingDao.clear()
        tokenStore.clear()
    }

    /** Regista o consentimento dado no onboarding (requer sessão iniciada). */
    suspend fun registerConsent(notificationsAccepted: Boolean) {
        api.registerConsent(
            requireBearer(),
            ConsentRequest(TERMS_VERSION, notificationsAccepted)
        )
    }

    private fun requireBearer(): String =
        tokenStore.bearer ?: error("Sessão expirada. Inicia sessão novamente.")

    companion object {
        const val TERMS_VERSION = "1.0"
        const val PRIVACY_POLICY_URL = "https://folhetosmart.pt/privacidade"
        const val TERMS_URL = "https://folhetosmart.pt/termos"
    }
}
