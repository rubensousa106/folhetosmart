package com.folhetosmart.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.ui.Validators
import com.folhetosmart.ui.components.ValidatedTextField

/** Recuperação de palavra-passe: o utilizador indica o email e recebe uma temporária. */
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(factory = ForgotPasswordViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Recuperar palavra-passe",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (state.done) {
            Text(
                "Se existir uma conta com este email, enviámos uma palavra-passe " +
                    "temporária. Entra com ela e define uma nova — é válida por 1 hora.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar ao início de sessão")
            }
        } else {
            Text(
                "Indica o teu email. Enviamos-te uma palavra-passe temporária para " +
                    "entrares e definires uma nova.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            ValidatedTextField(
                value = email,
                onValueChange = { email = it; emailError = null },
                label = "Email",
                error = emailError,
                keyboardType = KeyboardType.Email
            )
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    emailError = Validators.email(email)
                    if (emailError == null) viewModel.submit(email)
                },
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.submitting) "A enviar…" else "Enviar palavra-passe temporária")
            }
            TextButton(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar")
            }
        }
    }
}
