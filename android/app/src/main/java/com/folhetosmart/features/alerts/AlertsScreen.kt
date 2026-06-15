package com.folhetosmart.features.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.folhetosmart.data.api.AlertDto
import com.folhetosmart.ui.components.EmptyView
import com.folhetosmart.ui.components.Formatters
import com.folhetosmart.ui.components.LoadingView

/** Ecrã Alertas: sessão + gestão de alertas de preço. */
@Composable
fun AlertsScreen(viewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is AlertsUiState.Loading -> LoadingView()

        is AlertsUiState.NeedsLogin -> LoginForm(
            submitting = s.submitting,
            error = s.error,
            onLogin = viewModel::login,
            onRegister = viewModel::register
        )

        is AlertsUiState.LoggedIn -> AlertsList(
            state = s,
            onDelete = viewModel::deleteAlert,
            onRefresh = viewModel::loadAlerts,
            onLogout = viewModel::logout
        )
    }
}

@Composable
private fun LoginForm(
    submitting: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🔔 Alertas de preço",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Inicia sessão para receberes uma notificação quando os teus produtos baixarem de preço.",
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
            label = { Text("Palavra-passe") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
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
            onClick = { onLogin(email.trim(), password) },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (submitting) "A entrar…" else "Iniciar sessão")
        }
        OutlinedButton(
            onClick = { onRegister(email.trim(), password) },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Criar conta")
        }
    }
}

@Composable
private fun AlertsList(
    state: AlertsUiState.LoggedIn,
    onDelete: (AlertDto) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Os teus alertas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    state.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRefresh) { Text("Atualizar") }
            TextButton(onClick = onLogout) { Text("Sair") }
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (state.refreshing) {
            LoadingView("A carregar alertas…")
        } else if (state.alerts.isEmpty()) {
            EmptyView(
                emoji = "🔕",
                message = "Ainda não tens alertas.\nCria um a partir do ecrã Comparar (ícone 🔔)."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.alerts, key = { it.id }) { alert ->
                    AlertRow(alert, onDelete = { onDelete(alert) })
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertDto, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    alert.productDisplayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                val condition = buildList {
                    alert.targetPrice?.let { add("abaixo de ${Formatters.price(it)}") }
                    if (alert.anyPromotion) add("qualquer promoção")
                }.joinToString(" ou ")
                if (condition.isNotBlank()) {
                    Text(
                        "Avisar: $condition",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remover alerta",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
