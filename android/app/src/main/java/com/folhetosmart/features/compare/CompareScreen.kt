package com.folhetosmart.features.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.data.api.ProductDto
import com.folhetosmart.ui.components.EmptyView
import com.folhetosmart.ui.components.ErrorView
import com.folhetosmart.ui.components.LoadingView
import com.folhetosmart.ui.components.ProductPriceCard

/** Ecrã Comparar: pesquisa de produto -> preços em todos os supermercados. */
@Composable
fun CompareScreen(viewModel: CompareViewModel = viewModel(factory = CompareViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Pesquisar produto (ex.: doritos)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )

        when (val s = state) {
            is CompareUiState.Idle -> EmptyView(
                emoji = "🔍",
                message = "Pesquisa um produto para comparar preços\nentre os 5 supermercados."
            )

            is CompareUiState.Searching -> LoadingView("A pesquisar…")

            is CompareUiState.NoResults -> EmptyView(
                emoji = "🤷",
                message = "Sem resultados para “${s.query}”.\nExperimenta outro termo."
            )

            is CompareUiState.Error -> ErrorView(s.message, onRetry = viewModel::backToResults)

            is CompareUiState.Results ->
                ProductResults(s.products, s.isPromotions, viewModel::selectProduct)

            is CompareUiState.LoadingPrices ->
                LoadingView("A carregar preços de ${s.product.displayName}…")

            is CompareUiState.Prices -> PricesView(
                state = s,
                onBack = viewModel::backToResults,
                onCreateAlert = viewModel::createAlert
            )
        }
    }

    SnackbarHost(hostState = snackbarHost)
}

@Composable
private fun ProductResults(
    products: List<ProductDto>,
    isPromotions: Boolean,
    onSelect: (ProductDto) -> Unit
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isPromotions) {
            item(key = "promo-header") {
                Text(
                    "🔥 Promoções desta semana",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        items(products, key = { it.id }) { product ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(product) }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        product.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    product.brand?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PricesView(
    state: CompareUiState.Prices,
    onBack: () -> Unit,
    onCreateAlert: (Double?, Boolean) -> Unit
) {
    var showAlertDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                state.product.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAlertDialog = true }) {
                Icon(Icons.Filled.NotificationAdd, contentDescription = "Criar alerta")
            }
        }

        if (state.offline) {
            Text(
                "📡 Sem ligação — preços da última consulta",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (state.prices.isEmpty()) {
            EmptyView(
                emoji = "🏷️",
                message = "Nenhum supermercado tem este produto\nem folheto esta semana."
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.prices, key = { it.supermarketSlug }) { price ->
                    ProductPriceCard(price)
                }
            }
        }
    }

    if (showAlertDialog) {
        CreateAlertDialog(
            productName = state.product.displayName,
            onDismiss = { showAlertDialog = false },
            onConfirm = { target, anyPromo ->
                showAlertDialog = false
                onCreateAlert(target, anyPromo)
            }
        )
    }
}

@Composable
private fun CreateAlertDialog(
    productName: String,
    onDismiss: () -> Unit,
    onConfirm: (Double?, Boolean) -> Unit
) {
    var priceText by remember { mutableStateOf("") }
    var anyPromotion by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar alerta") },
        text = {
            Column {
                Text("Avisar quando “$productName” ficar mais barato.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Preço-alvo (€) — opcional") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = anyPromotion, onCheckedChange = { anyPromotion = it })
                    Text("Avisar em qualquer promoção")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = priceText.replace(',', '.').toDoubleOrNull()
                onConfirm(target, anyPromotion)
            }) { Text("Criar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
