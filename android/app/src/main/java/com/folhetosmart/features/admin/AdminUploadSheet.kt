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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.ui.theme.FolhetoSmartGreen
import com.folhetosmart.ui.theme.SavingsBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Conteúdo do bottom sheet "Upload de Folheto" (só ADMIN). Reutiliza o
 * [AdminViewModel]: escolher supermercado, período (De/Até), selecionar PDF e
 * enviar. Ao concluir (✅ N produtos), fecha e avisa o ecrã para atualizar.
 */
@Composable
fun AdminUploadSheet(
    onUploaded: () -> Unit,
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

    // Concluído: mostra 2s e fecha, atualizando a lista de supermercados.
    LaunchedEffect(state.phase) {
        if (state.phase is UploadPhase.Done) {
            delay(2_000)
            onUploaded()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "📋 Upload de Folheto",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        SupermarketDropdown(
            items = state.supermarkets.map { it.name to it.slug },
            selectedSlug = state.selectedSlug,
            enabled = !state.isBusy && state.supermarkets.isNotEmpty(),
            onSelect = viewModel::selectSupermarket
        )

        // Período de validade (semana segunda–domingo, navegável).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = viewModel::previousWeek, enabled = !state.isBusy) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Semana anterior")
            }
            Text(
                "De: ${state.validFrom}   Até: ${state.validUntil}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = viewModel::nextWeek, enabled = !state.isBusy) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Semana seguinte")
            }
        }

        state.previewFilename?.let {
            Text(
                "Nome gerado: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = { pdfPicker.launch("application/pdf") },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(state.picked?.label ?: "Selecionar PDF do telemóvel")
        }

        UploadAction(state = state, onUpload = viewModel::upload, onRetry = viewModel::retry)
    }
}

@Composable
private fun UploadAction(
    state: AdminUiState,
    onUpload: () -> Unit,
    onRetry: () -> Unit
) {
    when (val phase = state.phase) {
        is UploadPhase.Uploading -> BusyRow("☁️ A enviar para o Google Drive…")
        is UploadPhase.Processing -> BusyRow("🤖 A analisar folheto com IA… (~1-2 min)")

        is UploadPhase.Done -> Banner(
            "✅ ${phase.productsImported} produtos importados",
            SavingsBadge, FolhetoSmartGreen
        )

        is UploadPhase.Error -> {
            Banner(
                "❌ Erro: ${phase.message}",
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer
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
            Text("Enviar para Google Drive")
        }
    }
}

@Composable
private fun BusyRow(text: String) {
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text)
    }
    LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
private fun Banner(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color
) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupermarketDropdown(
    items: List<Pair<String, String>>,   // (nome, slug)
    selectedSlug: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = items.firstOrNull { it.second == selectedSlug }?.first ?: "Seleciona…"

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
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (name, slug) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(slug)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Nome de apresentação de um PDF a partir do Uri (content resolver). */
private fun displayNameOf(context: Context, uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
    }
}
