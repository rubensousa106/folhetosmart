package com.folhetosmart.features.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.folhetosmart.ui.DISTRITOS_PT

/** Ecrã Definições — conta (perfil) + privacidade e dados (RGPD). */
@Composable
fun SettingsScreen(
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshSession() }

    // JSON exportado -> abre a folha de partilha do Android.
    LaunchedEffect(state.exportedJson) {
        state.exportedJson?.let { json ->
            shareJson(context, json)
            viewModel.consumeExport()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Definições",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // A minha conta — editar nome, email, palavra-passe, distrito e cidade.
        if (state.loggedIn) {
            AccountCard(
                state = state,
                onSaveProfile = viewModel::saveProfile,
                onChangeEmail = viewModel::changeEmail,
                onChangePassword = viewModel::changePassword
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Privacidade e Dados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                if (!state.loggedIn) {
                    Text(
                        "Inicia sessão no separador Alertas para exportares ou eliminares os teus dados.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Exportar dados (Art. 20.º RGPD)
                    OutlinedButton(
                        onClick = viewModel::exportData,
                        enabled = !state.exporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text(
                            if (state.exporting) "  A exportar…" else "  Exportar os meus dados"
                        )
                    }
                    Text(
                        "Recebe um ficheiro JSON com todos os teus dados guardados na app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    // Eliminar conta (Art. 17.º RGPD)
                    Button(
                        onClick = { showDeleteDialog = true },
                        enabled = !state.deleting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text(
                            if (state.deleting) "  A eliminar…" else "  Eliminar conta e dados"
                        )
                    }
                    Text(
                        "Remove permanentemente a tua conta e todos os dados associados. Esta ação é irreversível.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                // Documentos legais abertos em ecrãs internos da app
                // (texto local — funcionam sem internet).
                LegalLink(
                    label = "Política de Privacidade",
                    onClick = onOpenPrivacyPolicy
                )
                LegalLink(
                    label = "Termos de Utilização",
                    onClick = onOpenTerms
                )
            }
        }

        state.message?.let {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::consumeMessage) { Text("OK") }
                }
            }
        }

        // Fix 1: terminar sessão — limpa o token e volta ao ecrã de Login.
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Text("  Sair")
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar conta e dados") },
            text = {
                Text(
                    "Tens a certeza? Esta ação é permanente e não pode ser desfeita. " +
                        "Todos os teus alertas e lista de compras serão eliminados."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteAccount()
                }) {
                    Text(
                        "Eliminar permanentemente",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

/** "A minha conta" — editar nome, distrito/cidade, email e palavra-passe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountCard(
    state: SettingsUiState,
    onSaveProfile: (String, String?, String) -> Unit,
    onChangeEmail: (String, String) -> Unit,
    onChangePassword: (String, String) -> Unit
) {
    var name by remember(state.name) { mutableStateOf(state.name) }
    var district by remember(state.district) { mutableStateOf(state.district) }
    var city by remember(state.city) { mutableStateOf(state.city) }
    var expanded by remember { mutableStateOf(false) }

    var newEmail by remember(state.email) { mutableStateOf(state.email) }
    var emailPwd by remember { mutableStateOf("") }

    var curPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "A minha conta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            // --- Perfil: nome + distrito + cidade ---
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nome") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = district ?: "", onValueChange = {}, readOnly = true,
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
                            onClick = { district = option; expanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = city, onValueChange = { city = it },
                label = { Text("Cidade") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSaveProfile(name, district, city) },
                enabled = !state.savingProfile,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.savingProfile) "A guardar…" else "Guardar perfil") }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // --- Email (exige a palavra-passe atual) ---
            OutlinedTextField(
                value = newEmail, onValueChange = { newEmail = it },
                label = { Text("Email") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = emailPwd, onValueChange = { emailPwd = it },
                label = { Text("Palavra-passe atual") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onChangeEmail(emailPwd, newEmail); emailPwd = "" },
                enabled = !state.savingEmail && emailPwd.isNotBlank() &&
                    newEmail.isNotBlank() && newEmail != state.email,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.savingEmail) "A alterar…" else "Alterar email") }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // --- Palavra-passe ---
            OutlinedTextField(
                value = curPwd, onValueChange = { curPwd = it },
                label = { Text("Palavra-passe atual") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newPwd, onValueChange = { newPwd = it },
                label = { Text("Nova palavra-passe (mín. 8)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onChangePassword(curPwd, newPwd); curPwd = ""; newPwd = "" },
                enabled = !state.savingPassword && curPwd.isNotBlank() && newPwd.length >= 8,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.savingPassword) "A alterar…" else "Alterar palavra-passe") }
        }
    }
}

@Composable
private fun LegalLink(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClick) {
            Text("ver")
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private fun shareJson(context: Context, json: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "Os meus dados — FolhetoSmart")
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(intent, "Guardar os meus dados"))
}
