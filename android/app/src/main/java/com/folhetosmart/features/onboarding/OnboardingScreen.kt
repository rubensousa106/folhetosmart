package com.folhetosmart.features.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.features.legal.PrivacyPolicyScreen
import com.folhetosmart.features.legal.TermsOfServiceScreen
import com.folhetosmart.ui.DISTRITOS_PT

/**
 * Registo em 2 passos (Fix 1):
 *  Passo 1 — conta + consentimentos RGPD;
 *  Passo 2 — localização para o folheto Aldi (opcional).
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showNotificationRationale by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    // Pedido de permissão POST_NOTIFICATIONS (Android 13+).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onFinish() }

    // Registo completo (passo 2 concluído) -> notificações ou termina.
    LaunchedEffect(state.locationDone) {
        if (state.locationDone) {
            if (state.notificationsAccepted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                showNotificationRationale = true
            } else {
                onFinish()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!state.accountCreated) {
            AccountStep(
                submitting = state.submitting,
                error = state.error,
                onCreate = viewModel::createAccount,
                onShowTerms = { showTerms = true },
                onShowPrivacy = { showPrivacyPolicy = true },
                onBackToLogin = onBackToLogin
            )
        } else {
            LocationStep(
                submitting = state.submitting,
                error = state.error,
                onSave = viewModel::saveLocation,
                onSkip = viewModel::skipLocation
            )
        }

        // Overlays dos documentos legais (ecrãs internos com texto local).
        if (showPrivacyPolicy) {
            PrivacyPolicyScreen(onBack = { showPrivacyPolicy = false })
        }
        if (showTerms) {
            TermsOfServiceScreen(onBack = { showTerms = false })
        }
    }

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = {
                showNotificationRationale = false
                onFinish()
            },
            title = { Text("Notificações") },
            text = {
                Text(
                    "Ativa as notificações para receberes alertas quando um " +
                        "produto que segues entra em promoção."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onFinish()
                    }
                }) { Text("Ativar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotificationRationale = false
                    onFinish()
                }) { Text("Agora não") }
            }
        )
    }
}

/** Passo 1 — criação de conta com consentimento explícito (RGPD). */
@Composable
private fun AccountStep(
    submitting: Boolean,
    error: String?,
    onCreate: (String, String, Boolean) -> Unit,
    onShowTerms: () -> Unit,
    onShowPrivacy: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }
    var notificationsAccepted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Criar conta",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Passo 1 de 2 — dados de acesso",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Palavra-passe (mín. 8 caracteres)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // Obrigatória para avançar.
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it })
            Column(Modifier.padding(top = 12.dp)) {
                Text(
                    "Li e aceito os Termos de Utilização e a Política de Privacidade",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    TextButton(onClick = onShowTerms) { Text("Ver Termos") }
                    TextButton(onClick = onShowPrivacy) { Text("Ver Política") }
                }
            }
        }

        // Opcional e independente.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = notificationsAccepted,
                onCheckedChange = { notificationsAccepted = it }
            )
            Text(
                "Aceito receber notificações de promoções",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onCreate(email, password, notificationsAccepted) },
            enabled = termsAccepted && !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (submitting) "A criar conta…" else "Criar conta")
        }

        TextButton(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Já tens conta? Entrar")
        }
    }
}

/** Passo 2 — localização para o folheto regional do Aldi (opcional). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationStep(
    submitting: Boolean,
    error: String?,
    onSave: (String?, String?) -> Unit,
    onSkip: () -> Unit
) {
    var district by remember { mutableStateOf<String?>(null) }
    var city by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "A tua zona",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Passo 2 de 2 — o folheto do Aldi varia por região. Escolhe o teu " +
                "distrito e cidade (sem GPS — podes alterar ou saltar).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = district ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Distrito") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DISTRITOS_PT.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            district = option
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("Cidade") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSave(district, city.ifBlank { null }) },
            enabled = !submitting && (district != null || city.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (submitting) "A guardar…" else "Guardar e continuar")
        }
        OutlinedButton(
            onClick = onSkip,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Saltar por agora")
        }
    }
}
