package com.folhetosmart.features.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.folhetosmart.data.api.SupermarketStatusDto
import com.folhetosmart.ui.components.ErrorView
import com.folhetosmart.ui.components.FlyerStatusChip
import com.folhetosmart.ui.components.Formatters
import com.folhetosmart.ui.components.LoadingView
import com.folhetosmart.ui.theme.FolhetoSmartGreen
import com.folhetosmart.ui.theme.PromotionBadge
import com.folhetosmart.ui.theme.SavingsBadge

/**
 * Ecrã "Sincronizar" — painel de controlo do automatizador (Tarefa 2).
 */
@Composable
fun SyncScreen(
    onSyncSuccess: () -> Unit = {},
    viewModel: SyncViewModel = viewModel(factory = SyncViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Fix 3.4 — após sincronizar com produtos, navega para Comparar (uma vez
    // por sync, identificado pelo finishedAt, para não repetir ao voltar).
    var navigatedFor by rememberSaveable { mutableStateOf<String?>(null) }
    (state as? SyncUiState.Done)?.let { done ->
        if (done.result.productsMatched > 0) {
            val key = done.result.finishedAt ?: "done"
            LaunchedEffect(key) {
                if (navigatedFor != key) {
                    navigatedFor = key
                    onSyncSuccess()
                }
            }
        }
    }

    // Fix 3 — upload manual de PDF: guarda o slug e abre o seletor (só PDF).
    var pendingUploadSlug by remember { mutableStateOf<String?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val slug = pendingUploadSlug
        pendingUploadSlug = null
        if (uri != null && slug != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) viewModel.uploadPdf(slug, bytes)
            }
        }
    }
    val onUploadPdf: (String) -> Unit = { slug ->
        pendingUploadSlug = slug
        pdfPicker.launch("application/pdf")
    }

    when (val s = state) {
        is SyncUiState.Loading -> LoadingView("A verificar folhetos…")

        is SyncUiState.Error -> ErrorView(s.message, onRetry = viewModel::refresh)

        is SyncUiState.WaitingForFlyers -> SyncContent(
            supermarkets = s.supermarkets,
            readyCount = s.ready,
            totalCount = s.total,
            offline = s.offline,
            lastSync = s.lastSync,
            onUploadPdf = onUploadPdf,
            button = {
                // Leitura (Fix 3): sempre ativo, mesmo sem todos os folhetos.
                Button(
                    onClick = viewModel::startSync,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▶  Ver promoções da semana")
                }
            }
        )

        is SyncUiState.ReadyToSync -> SyncContent(
            supermarkets = s.supermarkets,
            readyCount = s.supermarkets.size,
            totalCount = s.supermarkets.size,
            offline = false,
            lastSync = s.lastSync,
            onUploadPdf = onUploadPdf,
            button = {
                Button(
                    onClick = viewModel::startSync,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▶  Ver promoções da semana")
                }
            }
        )

        is SyncUiState.Syncing -> SyncContent(
            supermarkets = s.supermarkets,
            readyCount = s.supermarkets.size,
            totalCount = s.supermarkets.size,
            offline = false,
            lastSync = null,
            button = {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("A atualizar dados…")
                }
            }
        )

        is SyncUiState.Done -> SyncContent(
            supermarkets = s.supermarkets,
            readyCount = s.supermarkets.size,
            totalCount = s.supermarkets.size,
            offline = false,
            lastSync = LastSyncSummary(
                s.result.finishedAt,
                s.result.productsMatched,
                s.result.promotionsFound,
                s.result.avgSavingsPct
            ),
            doneBanner = true,
            // Após sincronizar, os que falharam mostram o botão 📎 (Estado 4).
            onUploadPdf = onUploadPdf,
            button = {
                Button(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Atualizar estado")
                }
            }
        )
    }
}

@Composable
private fun SyncContent(
    supermarkets: List<SupermarketStatusDto>,
    readyCount: Int,
    totalCount: Int,
    offline: Boolean,
    lastSync: LastSyncSummary?,
    doneBanner: Boolean = false,
    onUploadPdf: ((String) -> Unit)? = null,
    button: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (offline) {
            OfflineBanner()
        }
        if (doneBanner) {
            DoneBanner()
        }

        FlyersCard(supermarkets, readyCount, totalCount, onUploadPdf)

        button()

        if (lastSync != null) {
            LastSyncCard(lastSync)
        }
    }
}

/** Secção superior — estado dos folhetos, um chip por supermercado. */
@Composable
private fun FlyersCard(
    supermarkets: List<SupermarketStatusDto>,
    readyCount: Int,
    totalCount: Int,
    onUploadPdf: ((String) -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "📋 Folhetos desta semana",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))

            supermarkets.forEach { market ->
                FlyerStatusChip(
                    supermarketName = market.name,
                    syncStatus = market.syncStatus,
                    flyerAvailable = market.flyerAvailable,
                    availableSinceLabel = market.availableSince
                        ?.let { Formatters.shortDateTime(it) },
                    productsImported = market.productsImported,
                    syncedAtLabel = market.syncedAt?.let { Formatters.shortDateTime(it) },
                    syncSource = market.syncSource,
                    // Fix 3: 📎 — só aparece quando syncStatus == "error".
                    onUploadPdf = onUploadPdf?.let { upload -> { upload(market.slug) } }
                )
            }

            Spacer(Modifier.height(2.dp))
            Text(
                "$readyCount de $totalCount supermercados prontos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Secção inferior — última sincronização. */
@Composable
private fun LastSyncCard(lastSync: LastSyncSummary) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SavingsBadge)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val whenLabel = Formatters.longDateTime(lastSync.finishedAt)
            Text(
                if (whenLabel.isNotBlank()) "Última comparação: $whenLabel"
                else "Última comparação",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${lastSync.productsMatched} produtos · ${lastSync.promotionsFound} promoções",
                style = MaterialTheme.typography.bodyMedium
            )
            lastSync.avgSavingsPct?.let {
                Text(
                    "Poupança média identificada: ${Formatters.percent(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FolhetoSmartGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = PromotionBadge
    ) {
        Text(
            "📡 Sem ligação — a mostrar os últimos dados guardados",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun DoneBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SavingsBadge
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "✅ Dados atualizados — vê as promoções!",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = FolhetoSmartGreen
            )
        }
    }
}
