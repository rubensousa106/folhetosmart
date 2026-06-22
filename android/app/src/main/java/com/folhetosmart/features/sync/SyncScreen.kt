package com.folhetosmart.features.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.features.admin.AdminUploadSheet
import com.folhetosmart.ui.components.ErrorView
import com.folhetosmart.ui.components.LoadingView
import com.folhetosmart.ui.theme.ErrorRed
import com.folhetosmart.ui.theme.FolhetoSmartGreen

/**
 * Ecrã "Sincronizar" (modelo simples): há UM só ficheiro — os produtos
 * normalizados. Mostra ✓ se sincronizado, ✗ se não, e "Sincronizar agora"
 * descarrega-o (fica em cache para uso offline). O ADMIN tem, no fundo, a área
 * de upload de folhetos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    isAdmin: Boolean = false,
    viewModel: SyncViewModel = viewModel(factory = SyncViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdminSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SyncEvent.Checked -> snackbarHostState.showSnackbar(
                    if (event.hasData) "✅ Produtos atualizados" else "Sem novidades por enquanto"
                )
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                val success = data.visuals.message.startsWith("✅")
                Snackbar(
                    snackbarData = data,
                    containerColor = if (success) FolhetoSmartGreen else SnackbarDefaults.color,
                    contentColor = if (success) Color.White else SnackbarDefaults.contentColor
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val s = state) {
                is SyncUiState.Loading -> LoadingView("A sincronizar…")
                is SyncUiState.Error -> ErrorView(s.message, onRetry = viewModel::verify)
                is SyncUiState.Content -> SyncContent(
                    s = s,
                    isAdmin = isAdmin,
                    onVerify = viewModel::verify,
                    onOpenAdmin = { showAdminSheet = true }
                )
            }
        }
    }

    if (showAdminSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAdminSheet = false },
            sheetState = sheetState
        ) {
            AdminUploadSheet(
                onUploaded = {
                    showAdminSheet = false
                    viewModel.verify()
                }
            )
        }
    }
}

@Composable
private fun SyncContent(
    s: SyncUiState.Content,
    isAdmin: Boolean,
    onVerify: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "🛒 Produtos desta semana",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Válido de ${s.validityLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (s.checking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                // O único item: o ficheiro de produtos normalizados (✓ ou ✗).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (s.synced) {
                        Icon(
                            Icons.Filled.CheckCircle, contentDescription = "Sincronizado",
                            tint = FolhetoSmartGreen, modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Cancel, contentDescription = "Por sincronizar",
                            tint = ErrorRed, modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        "Produtos",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (s.synced) "${s.productCount} produtos" else "Por sincronizar",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (s.synced) FolhetoSmartGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (s.synced) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                if (!s.synced && s.errorMessage == null) {
                    Text(
                        "Toca em Sincronizar para descarregar os produtos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                s.errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
                }

                if (!s.lastCheckedLabel.isNullOrBlank()) {
                    Text(
                        "Última sincronização: ${s.lastCheckedLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onVerify,
                    enabled = !s.checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (s.checking) "A sincronizar…" else "🔄  Sincronizar agora")
                }
            }
        }

        if (isAdmin) {
            AdminSection(onOpenAdmin)
        }
    }
}

/** Secção só-ADMIN no fundo do ecrã Sincronizar. */
@Composable
private fun AdminSection(onOpenAdmin: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HorizontalDivider()
            Text(
                "⚙️ Área de Administração",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Fazer upload de folheto")
            }
        }
    }
}
