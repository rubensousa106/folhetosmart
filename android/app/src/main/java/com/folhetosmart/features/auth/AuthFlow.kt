package com.folhetosmart.features.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.folhetosmart.features.onboarding.OnboardingScreen

/**
 * Fluxo de autenticação à entrada da app (Fix 1):
 * Login por omissão; "Registar" abre o registo em 2 passos.
 */
@Composable
fun AuthFlow(onAuthenticated: () -> Unit) {
    var showRegister by remember { mutableStateOf(false) }

    if (showRegister) {
        OnboardingScreen(
            onFinish = onAuthenticated,
            onBackToLogin = { showRegister = false }
        )
    } else {
        LoginScreen(
            onLoggedIn = onAuthenticated,
            onGoToRegister = { showRegister = true }
        )
    }
}
