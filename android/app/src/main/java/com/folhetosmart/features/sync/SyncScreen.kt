package com.folhetosmart.features.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.ui.components.ErrorView
import com.folhetosmart.ui.components.Formatters
import com.folhetosmart.ui.components.LoadingView
import com.folhetosmart.ui.theme.PromotionBadge

/**
 * Ecrã "Sincronizar" do USER — leitura instantânea das promoções da semana já
 * processadas. Cartão simples com [🔄 Atualizar]; sem barra de progresso nem
 * estados por supermercado (isso é só do ecrã Admin).
 *
 * O parâmetro [onSyncSuccess] mantém-se por compatibilidade com a navegação,
 * mas o fluxo do USER não navega a partir daqui (usa o separador Comparar).
 */
@Composable
fun SyncScreen(
    onSyncSuccess: () -> Unit = {},
    viewModel: SyncViewModel = viewModel(factory = SyncViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is SyncUiState.Loading -> LoadingView("A carregar promoções…")
        is SyncUiState.Error -> ErrorView(s.message, onRetry = viewModel::refresh)
        is SyncUiState.Ready -> WeekSummary(s, onRefresh = viewModel::refresh)
    }
}

@Composable
private fun WeekSummary(s: SyncUiState.Ready, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (s.offline) OfflineBanner()

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "🛒 Promoções desta semana",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (s.hasData) {
                    s.lastUpdateIso?.let { iso ->
                        val whenLabel = Formatters.shortDateTime(iso)
                        if (whenLabel.isNotBlank()) {
                            Text(
                                "Última atualização: $whenLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "${s.totalProducts} produtos · ${s.supermarketsWithData} supermercados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("🔄  Atualizar")
                    }
                    Text(
                        "Válido de ${s.validityLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Ainda sem dados esta semana.\n" +
                            "Os folhetos são atualizados às quintas-feiras após as 10h00.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("🔄  Verificar agora")
                    }
                }
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
