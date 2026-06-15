package com.folhetosmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.folhetosmart.features.auth.AuthFlow
import com.folhetosmart.ui.navigation.FolhetoSmartRoot
import com.folhetosmart.ui.theme.FolhetoSmartTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as FolhetoSmartApp).container

        setContent {
            FolhetoSmartTheme {
                // Porta de entrada (Fix 1): com sessão válida em cache vai
                // direto ao ecrã principal; sem sessão, mostra o Login.
                var authenticated by remember {
                    mutableStateOf(container.tokenStore.isSessionValid())
                }

                if (authenticated) {
                    FolhetoSmartRoot(
                        onLogout = {
                            container.alertsRepository.logout() // limpa o token
                            authenticated = false               // volta ao Login
                        }
                    )
                } else {
                    AuthFlow(onAuthenticated = { authenticated = true })
                }
            }
        }
    }
}
