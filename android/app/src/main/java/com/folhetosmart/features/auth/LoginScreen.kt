package com.folhetosmart.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.ui.Validators
import com.folhetosmart.ui.components.ValidatedTextField

/**
 * Ecrã de Login — entrada da app quando não há sessão ativa (Fix 1).
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onGoToRegister: () -> Unit,
    onForgotPassword: () -> Unit = {},
    onMustChangePassword: (String) -> Unit = {},
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }
    // Entrou com palavra-passe temporária → leva a definir uma nova (passa a temporária).
    LaunchedEffect(state.requirePasswordChange) {
        if (state.requirePasswordChange) onMustChangePassword(password)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🛒", style = MaterialTheme.typography.displayMedium)
        Text(
            "FolhetoSmart",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Compara os folhetos. Poupa sempre.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        ValidatedTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = "Email",
            error = emailError,
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(8.dp))
        ValidatedTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = "Palavra-passe",
            error = passwordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardType = KeyboardType.Password
        )

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                emailError = Validators.required(email, "O email é obrigatório")
                passwordError = Validators.required(password, "A palavra-passe é obrigatória")
                if (emailError == null && passwordError == null) {
                    viewModel.login(email, password)
                }
            },
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.submitting) "A entrar…" else "Entrar")
        }

        TextButton(onClick = onGoToRegister) {
            Text("Não tens conta? Registar")
        }
        TextButton(onClick = onForgotPassword) {
            Text("Esqueceste-te da palavra-passe?")
        }
    }
}
