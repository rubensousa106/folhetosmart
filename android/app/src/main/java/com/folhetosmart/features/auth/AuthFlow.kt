package com.folhetosmart.features.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.folhetosmart.features.onboarding.OnboardingScreen

/**
 * Fluxo de autenticação à entrada da app:
 * Login por omissão; "Registar" abre o registo em 2 passos; "Esqueceste-te da
 * palavra-passe?" abre a recuperação; entrar com uma palavra-passe temporária
 * força a definição de uma nova antes de continuar.
 */
@Composable
fun AuthFlow(onAuthenticated: () -> Unit) {
    var screen by remember { mutableStateOf<AuthScreen>(AuthScreen.Login) }

    when (val s = screen) {
        AuthScreen.Login -> LoginScreen(
            onLoggedIn = onAuthenticated,
            onGoToRegister = { screen = AuthScreen.Register },
            onForgotPassword = { screen = AuthScreen.Forgot },
            onMustChangePassword = { temp -> screen = AuthScreen.SetNew(temp) }
        )
        AuthScreen.Register -> OnboardingScreen(
            onFinish = onAuthenticated,
            onBackToLogin = { screen = AuthScreen.Login }
        )
        AuthScreen.Forgot -> ForgotPasswordScreen(
            onBackToLogin = { screen = AuthScreen.Login }
        )
        is AuthScreen.SetNew -> SetNewPasswordScreen(
            tempPassword = s.tempPassword,
            onDone = onAuthenticated
        )
    }
}

/** Ecrãs do fluxo de autenticação. */
private sealed interface AuthScreen {
    data object Login : AuthScreen
    data object Register : AuthScreen
    data object Forgot : AuthScreen
    data class SetNew(val tempPassword: String) : AuthScreen
}
