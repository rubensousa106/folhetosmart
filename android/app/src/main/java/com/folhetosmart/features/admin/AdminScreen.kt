package com.folhetosmart.features.admin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.data.api.AdminFlyerStatusDto
import com.folhetosmart.ui.components.Formatters
import com.folhetosmart.ui.theme.ErrorRed
import com.folhetosmart.ui.theme.FolhetoSmartGreen
import com.folhetosmart.ui.theme.SavingsBadge
import com.folhetosmart.ui.theme.WaitingGrey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ecrã de administração (só ADMIN — Fix 3). Permite carregar um folheto PDF que
 * é guardado no Google Drive e processado com IA, mostra o estado dos folhetos
 * da semana e permite forçar a sincronização automática.
 */
@Composable
fun AdminScreen(
    viewModel: AdminViewModel = viewModel(factory = AdminViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) {
                    viewModel.onPdfPicked(displayNameOf(context, uri) ?: "folheto.pdf", bytes)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "⚙️  Painel de Administração",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            state.adminEmail?.let { email ->
                Text(
                    email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        UploadCard(
            state = state,
            onSelectSupermarket = viewModel::selectSupermarket,
            onPrevWeek = viewModel::previousWeek,
            onNextWeek = viewModel::nextWeek,
            onPickPdf = { pdfPicker.launch("application/pdf") },
            onUpload = viewModel::upload,
            onRetry = viewModel::retry
        )

        FlyersStatusCard(
            state = state,
            onForceSync = viewModel::forceSync,
            onReload = viewModel::loadStatus
        )
    }
}

// -- Formulário de upload ----------------------------------------------------
@Composable
private fun UploadCard(
    state: AdminUiState,
    onSelectSupermarket: (String) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onPickPdf: () -> Unit,
    onUpload: () -> Unit,
    onRetry: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "📋 Upload de folhetos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SupermarketDropdown(
                items = state.supermarkets,
                selectedSlug = state.selectedSlug,
                enabled = !state.isBusy && state.supermarkets.isNotEmpty(),
                onSelect = onSelectSupermarket
            )

            WeekSelector(
                label = "De ${state.validFrom}   ·   Até ${state.validUntil}",
                enabled = !state.isBusy,
                onPrev = onPrevWeek,
                onNext = onNextWeek
            )

            OutlinedButton(
                onClick = onPickPdf,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.picked?.label ?: "Selecionar PDF do telemóvel")
            }

            state.previewFilename?.let { name ->
                Text(
                    "Guardado no Drive como: $name",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            UploadAction(state = state, onUpload = onUpload, onRetry = onRetry)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupermarketDropdown(
    items: List<AdminFlyerStatusDto>,
    selectedSlug: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = items.firstOrNull { it.slug == selectedSlug }?.name ?: "Seleciona…"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Supermercado") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name) },
                    onClick = {
                        onSelect(item.slug)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun WeekSelector(
    label: String,
    enabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "📅 Período de validade",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrev, enabled = enabled) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Semana anterior")
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onNext, enabled = enabled) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Semana seguinte")
            }
        }
    }
}

/** Botão principal de upload — reflete a máquina de estados (Fix 5). */
@Composable
private fun UploadAction(
    state: AdminUiState,
    onUpload: () -> Unit,
    onRetry: () -> Unit
) {
    when (val phase = state.phase) {
        is UploadPhase.Uploading -> BusyButton("A enviar para o Drive…")

        is UploadPhase.Processing ->
            BusyButton("A extrair produtos com IA… (pode demorar 1-2 min)")

        is UploadPhase.Done -> Banner(
            text = "✅  ${phase.productsImported} produtos importados",
            container = SavingsBadge,
            content = FolhetoSmartGreen
        )

        is UploadPhase.Error -> {
            Banner(
                text = "❌  ${phase.message}",
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Tentar novamente")
            }
        }

        else -> Button(
            onClick = onUpload,
            enabled = state.canUpload,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Fazer upload para o Drive")
        }
    }
}

@Composable
private fun BusyButton(text: String) {
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text)
    }
}

@Composable
private fun Banner(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = content
        )
    }
}

// -- Estado dos folhetos -----------------------------------------------------
@Composable
private fun FlyersStatusCard(
    state: AdminUiState,
    onForceSync: () -> Unit,
    onReload: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "📊 Estado dos folhetos desta semana",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.week.isNotBlank()) {
                    Text(
                        state.week,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                state.statusLoading && state.supermarkets.isEmpty() ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("A carregar…")
                    }

                state.statusError != null && state.supermarkets.isEmpty() -> Column {
                    Text(state.statusError, color = ErrorRed)
                    TextButton(onClick = onReload) { Text("Tentar novamente") }
                }

                else -> state.supermarkets.forEach { AdminFlyerRow(it) }
            }

            Spacer(Modifier.size(2.dp))
            Button(
                onClick = onForceSync,
                enabled = !state.syncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("A sincronizar…")
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Forçar sincronização agora")
                }
            }

            // Erro não-bloqueante quando já há lista visível.
            if (state.statusError != null && state.supermarkets.isNotEmpty()) {
                Text(
                    state.statusError,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed
                )
            }
        }
    }
}

@Composable
private fun AdminFlyerRow(market: AdminFlyerStatusDto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (market.hasFlyer) SavingsBadge else WaitingBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (market.hasFlyer) {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Com folheto",
                    tint = FolhetoSmartGreen, modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    Icons.Filled.HourglassEmpty, contentDescription = "Sem folheto",
                    tint = WaitingGrey, modifier = Modifier.size(22.dp)
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    market.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    market.driveFilename ?: if (market.hasFlyer) "Disponível" else "Sem folheto",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (market.hasFlyer) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${market.productsImported} produtos",
                        style = MaterialTheme.typography.labelMedium,
                        color = FolhetoSmartGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    market.syncedAt?.let {
                        Text(
                            Formatters.shortDateTime(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Lê o nome de apresentação de um PDF a partir do seu Uri (content resolver). */
private fun displayNameOf(context: Context, uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            cursor.getString(idx)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }
}

// Cinza-claro suave para supermercados ainda sem folheto.
private val WaitingBg = androidx.compose.ui.graphics.Color(0xFFF1F1F1)
