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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Definição OBRIGATÓRIA de uma nova palavra-passe depois de entrar com a temporária.
 * Recebe a [tempPassword] (usada para autenticar a troca); ao concluir chama [onDone].
 */
@Composable
fun SetNewPasswordScreen(
    tempPassword: String,
    onDone: () -> Unit,
    viewModel: SetNewPasswordViewModel = viewModel(factory = SetNewPasswordViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newPwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var newPwdError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Definir nova palavra-passe",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Entraste com uma palavra-passe temporária. Define agora a tua nova " +
                "palavra-passe para continuares.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        ValidatedTextField(
            value = newPwd,
            onValueChange = { newPwd = it; newPwdError = null },
            label = "Nova palavra-passe (mín. 8)",
            error = newPwdError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardType = KeyboardType.Password
        )
        Spacer(Modifier.height(8.dp))
        ValidatedTextField(
            value = confirm,
            onValueChange = { confirm = it; confirmError = null },
            label = "Confirmar palavra-passe",
            error = confirmError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardType = KeyboardType.Password
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
                newPwdError = Validators.password(newPwd)
                confirmError = Validators.confirmPassword(confirm, newPwd)
                if (newPwdError == null && confirmError == null) {
                    viewModel.submit(tempPassword, newPwd)
                }
            },
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.submitting) "A guardar…" else "Guardar nova palavra-passe")
        }
    }
}
