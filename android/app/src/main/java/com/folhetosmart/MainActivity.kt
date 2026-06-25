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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as FolhetoSmartApp).container
        // Destino vindo de uma notificação push (ex.: "novos produtos" → "sync").
        val startRoute = intent?.getStringExtra(EXTRA_OPEN_ROUTE)

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

    companion object {
        /** Extra do intent: rota a abrir vinda de uma notificação push (ex.: "sync"). */
        const val EXTRA_OPEN_ROUTE = "open_route"
    }
}
