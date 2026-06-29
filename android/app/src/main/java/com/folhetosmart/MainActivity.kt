package com.folhetosmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.folhetosmart.features.auth.AuthFlow
import com.folhetosmart.ui.navigation.FolhetoSmartRoot
import com.folhetosmart.ui.theme.FolhetoSmartTheme
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as FolhetoSmartApp).container
        // Destino vindo de uma notificação push (ex.: "novos produtos" → "sync").
        val startRoute = intent?.getStringExtra(EXTRA_OPEN_ROUTE)

        // Pede/mostra o consentimento de publicidade (RGPD) se for necessário.
        requestAdsConsent()

        setContent {
            FolhetoSmartTheme {
                // Porta de entrada (Fix 1): com sessão válida em cache vai
                // direto ao ecrã principal; sem sessão, mostra o Login.
                var authenticated by remember {
                    mutableStateOf(container.tokenStore.isSessionValid())
                }
                val scope = rememberCoroutineScope()

                if (authenticated) {
                    FolhetoSmartRoot(
                        // O separador Admin só aparece para role ADMIN (Fix 2).
                        isAdmin = container.tokenStore.isAdmin,
                        startRoute = startRoute,
                        onLogout = {
                            // Limpa token + dados locais (lista + cache) e volta
                            // ao Login. Sem sessão não se vê nem se mexe em nada.
                            scope.launch {
                                container.logout()
                                authenticated = false
                            }
                        }
                    )
                } else {
                    AuthFlow(onAuthenticated = { authenticated = true })
                }
            }
        }
    }

    /**
     * Consentimento de publicidade (UMP/RGPD): atualiza a informação de consentimento
     * e mostra o formulário se for exigido (ex.: utilizadores na UE). Falhas são
     * ignoradas — nesse caso a app segue sem mostrar anúncios personalizados.
     */
    private fun requestAdsConsent() {
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { } },
            { }
        )
    }

    companion object {
        /** Extra do intent: rota a abrir vinda de uma notificação push (ex.: "sync"). */
        const val EXTRA_OPEN_ROUTE = "open_route"
    }
}
