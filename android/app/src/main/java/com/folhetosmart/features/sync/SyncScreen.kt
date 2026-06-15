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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.data.api.SupermarketStatusDto
import com.folhetosmart.ui.components.ErrorView
import com.folhetosmart.ui.components.FlyerStatusChip
import com.folhetosmart.ui.components.Formatters
import com.folhetosmart.ui.components.LoadingView
import com.folhetosmart.ui.theme.FolhetoSmartGreen
import com.folhetosmart.ui.theme.PromotionBadge
import com.folhetosmart.ui.theme.SavingsBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ecrã "Sincronizar" (Fix 3). Reflete honestamente o estado do servidor:
 *  - nunca mostra "Ver promoções da semana" sem dados reais desta semana;
 *  - "Atualizar estado" é só leitura (não dispara processamento);
 *  - durante o processamento mostra barra de progresso + tempo estimado/limite;
 *  - só navega para Comparar (e mostra o resumo de sucesso) quando o
 *    processamento termina mesmo com sucesso.
 */
@Composable
fun SyncScreen(
    onSyncSuccess: () -> Unit = {},
    viewModel: SyncViewModel = viewModel(factory = SyncViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Upload manual de PDF (botão 📎 nos supermercados em erro).
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

        is SyncUiState.Idle -> SyncContent(
            supermarkets = s.supermarkets,
            readyLabel = "${s.supermarkets.count { it.flyerAvailable }} de ${s.supermarkets.size} supermercados com folheto",
            offline = s.offline,
            lastSync = s.lastSync,
            onUploadPdf = onUploadPdf,
            header = null,
            footer = {
                if (s.hasCurrentWeekData) {
                    Button(
                        onClick = { viewModel.prefetchWeek(); onSyncSuccess() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("▶  Ver promoções da semana (${s.totalProductsThisWeek} produtos)")
                    }
                } else {
                    NoDataCard()
                }
                RefreshRow(s.lastCheckedLabel, onRefresh = viewModel::refresh)
            }
        )

        is SyncUiState.Processing -> SyncContent(
            supermarkets = s.supermarkets,
            readyLabel = "${s.doneCount} de ${s.totalCount} concluídos",
            offline = false,
            lastSync = null,
            onUploadPdf = null,
            header = { ProgressCard(s.doneCount, s.totalCount, s.etaSeconds) },
            footer = null
        )

        is SyncUiState.Completed -> {
            // Mostra o resumo 3s e só depois navega para Comparar (única vez).
            LaunchedEffect(Unit) {
                delay(3_000)
                onSyncSuccess()
            }
            SyncContent(
                supermarkets = s.supermarkets,
                readyLabel = "${s.supermarkets.size} supermercados",
                offline = false,
                lastSync = null,
                onUploadPdf = null,
                header = { CompletionBanner(s.productsMatched, s.avgSavingsPct) },
                footer = null
            )
        }

        is SyncUiState.AllFailed -> SyncContent(
            supermarkets = s.supermarkets,
            readyLabel = "${s.supermarkets.size} supermercados",
            offline = false,
            lastSync = null,
            onUploadPdf = onUploadPdf,
            header = { AllFailedCard() },
            footer = { RefreshRow(null, onRefresh = viewModel::refresh) }
        )
    }
}

@Composable
private fun SyncContent(
    supermarkets: List<SupermarketStatusDto>,
    readyLabel: String,
    offline: Boolean,
    lastSync: LastSyncSummary?,
    onUploadPdf: ((String) -> Unit)?,
    header: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (offline) OfflineBanner()
        header?.invoke()
        FlyersCard(supermarkets, readyLabel, onUploadPdf)
        footer?.invoke()
        if (lastSync != null) LastSyncCard(lastSync)
    }
}

/** Barra de progresso global + tempo estimado/limite (Fix 3). */
@Composable
private fun ProgressCard(doneCount: Int, totalCount: Int, etaSeconds: Int) {
    val fraction = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val etaLabel = when {
        etaSeconds <= 0 -> "quase concluído"
        etaSeconds < 60 -> "menos de 1 minuto"
        else -> "~${(etaSeconds + 59) / 60} minutos"
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ProcessingBg)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "🔄 A processar folhetos…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Text("$doneCount de $totalCount", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Tempo estimado: $etaLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Tempo limite: cancela em 10 minutos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompletionBanner(productsMatched: Int, avgSavingsPct: Double?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SavingsBadge
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "✅ Sincronização concluída!",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = FolhetoSmartGreen
            )
            val savings = avgSavingsPct?.let { " · Poupança média ${Formatters.percent(it)}" } ?: ""
            Text(
                "$productsMatched produtos$savings",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AllFailedCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            "❌ Não foi possível sincronizar automaticamente. " +
                "Carrega os PDFs manualmente usando o botão 📎.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

/** Mensagem honesta quando ainda não há dados desta semana. */
@Composable
private fun NoDataCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = PromotionBadge
    ) {
        Text(
            "Ainda não há dados desta semana. Aguarda a sincronização de quinta-feira.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Botão "Atualizar estado" (só leitura) + carimbo da última verificação. */
@Composable
private fun RefreshRow(lastCheckedLabel: String?, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Atualizar estado")
        }
        if (!lastCheckedLabel.isNullOrBlank()) {
            Text(
                "Última verificação: $lastCheckedLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Secção superior — estado dos folhetos, um chip por supermercado. */
@Composable
private fun FlyersCard(
    supermarkets: List<SupermarketStatusDto>,
    readyLabel: String,
    onUploadPdf: ((String) -> Unit)?
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
                    availableSinceLabel = market.availableSince?.let { Formatters.shortDateTime(it) },
                    productsImported = market.productsImported,
                    syncedAtLabel = market.syncedAt?.let { Formatters.shortDateTime(it) },
                    syncSource = market.syncSource,
                    progressMessage = market.progressMessage,
                    // 📎 só aparece quando syncStatus == "error".
                    onUploadPdf = onUploadPdf?.let { upload -> { upload(market.slug) } }
                )
            }

            Spacer(Modifier.height(2.dp))
            Text(
                readyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Secção inferior — última sincronização concluída. */
@Composable
private fun LastSyncCard(lastSync: LastSyncSummary) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SavingsBadge)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val whenLabel = Formatters.longDateTime(lastSync.finishedAt)
            Text(
                if (whenLabel.isNotBlank()) "Última comparação: $whenLabel" else "Última comparação",
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

// Azul-claro suave para o estado "a processar".
private val ProcessingBg = Color(0xFFE3F2FD)
