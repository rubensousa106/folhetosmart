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
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.folhetosmart.ui.DistritoCidadeFields
import com.folhetosmart.ui.UserAvatar
import kotlinx.coroutines.delay

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

    // A confirmação de sucesso desaparece sozinha (sem botão "OK").
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3000)
            viewModel.consumeMessage()
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
            // Confirmação — desaparece sozinha. Removido o botão "OK": ficava colado
            // ao "Sair" e um duplo toque por engano terminava a sessão.
            Card(Modifier.fillMaxWidth()) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
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

/** "A minha conta" — avatar (animal) → escolher o que editar: nome, zona, email, password. */
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
    var newEmail by remember(state.email) { mutableStateOf(state.email) }
    var emailPwd by remember { mutableStateOf("") }
    var curPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }

    var menuOpen by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // Cabeçalho: avatar (animal do utilizador) + nome/email + menu de edição.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    UserAvatar(
                        seed = state.email.ifBlank { "folhetosmart" },
                        onClick = { menuOpen = true }
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Alterar nome") },
                            onClick = { section = "nome"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Alterar zona") },
                            onClick = { section = "zona"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Alterar email") },
                            onClick = { section = "email"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Alterar palavra-passe") },
                            onClick = { section = "password"; menuOpen = false })
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        name.ifBlank { "A minha conta" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.email.isNotBlank()) {
                        Text(
                            state.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar conta")
                }
            }

            // Editor da secção escolhida (toca no avatar para escolher).
            when (section) {
                "nome" -> {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nome") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onSaveProfile(name, district, city) },
                        enabled = !state.savingProfile,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (state.savingProfile) "A guardar…" else "Guardar nome") }
                }
                "zona" -> {
                    Spacer(Modifier.height(12.dp))
                    DistritoCidadeFields(
                        distrito = district,
                        cidade = city,
                        onDistritoChange = { district = it; city = "" },
                        onCidadeChange = { city = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onSaveProfile(name, district, city) },
                        enabled = !state.savingProfile,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (state.savingProfile) "A guardar…" else "Guardar zona") }
                }
                "email" -> {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newEmail, onValueChange = { newEmail = it },
                        label = { Text("Novo email") }, singleLine = true,
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
                    Button(
                        onClick = { onChangeEmail(emailPwd, newEmail); emailPwd = "" },
                        enabled = !state.savingEmail && emailPwd.isNotBlank() &&
                            newEmail.isNotBlank() && newEmail != state.email,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (state.savingEmail) "A alterar…" else "Alterar email") }
                }
                "password" -> {
                    Spacer(Modifier.height(12.dp))
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
                    Button(
                        onClick = { onChangePassword(curPwd, newPwd); curPwd = ""; newPwd = "" },
                        enabled = !state.savingPassword && curPwd.isNotBlank() && newPwd.length >= 8,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (state.savingPassword) "A alterar…" else "Alterar palavra-passe") }
                }
                else -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Toca no avatar para alterar o nome, a zona, o email ou a palavra-passe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
