package com.folhetosmart.features.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.data.local.ShoppingItemEntity
import com.folhetosmart.ui.components.EmptyView
import com.folhetosmart.ui.theme.FolhetoSmartGreen
import com.folhetosmart.ui.theme.SavingsBadge

/** Ecrã Lista: lista de compras com otimização por supermercado. */
@Composable
fun ListScreen(viewModel: ListViewModel = viewModel(factory = ListViewModel.Factory)) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {

        // Pesquisa para adicionar produtos à lista.
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Adicionar produto à lista…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )

        if (state.searching) {
            Text(
                "A pesquisar…",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        state.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Resultados de pesquisa (para adicionar)
            items(state.searchResults, key = { "search-${it.id}" }) { product ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.addProduct(product) }
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Adicionar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(product.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Total da lista por supermercado (calculado localmente).
            state.totals?.let { totals ->
                item(key = "totals") { TotalsCard(totals) }
            }

            // Itens da lista, AGRUPADOS por supermercado.
            val grouped = items.groupBy { it.supermercado?.takeIf { s -> s.isNotBlank() } ?: "Sem supermercado" }
            grouped.forEach { (loja, lojaItems) ->
                item(key = "hdr-$loja") {
                    Text(
                        "🛒 $loja",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = FolhetoSmartGreen,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                    )
                }
                items(lojaItems, key = { it.productId }) { item ->
                    ShoppingItemRow(
                        item = item,
                        onIncrease = { viewModel.changeQuantity(item, +1) },
                        onDecrease = { viewModel.changeQuantity(item, -1) },
                        onRemove = { viewModel.remove(item) }
                    )
                }
            }

            if (items.isEmpty() && state.searchResults.isEmpty()) {
                item(key = "empty") {
                    EmptyView(
                        emoji = "🛒",
                        message = "A tua lista está vazia.\nPesquisa produtos para adicionar."
                    )
                }
            }
        }

        // Total da lista por supermercado (cálculo local, instantâneo).
        Button(
            onClick = viewModel::showTotals,
            enabled = items.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("💰 Total por supermercado")
        }
    }
}

/** Total da lista por supermercado + total geral (calculado localmente). */
@Composable
private fun TotalsCard(totals: ListTotals) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SavingsBadge)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "💰 Total por supermercado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            totals.perStore.forEach { st ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🏪 ${st.supermercado}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "€${String.format("%.2f", st.subtotal)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = FolhetoSmartGreen
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Total",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "€${String.format("%.2f", totals.grandTotal)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FolhetoSmartGreen
                )
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItemEntity,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (item.preco > 0.0) {
                    Text(
                        "€${String.format("%.2f", item.preco)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FolhetoSmartGreen
                    )
                }
            }
            IconButton(onClick = onDecrease) {
                Icon(Icons.Filled.Remove, contentDescription = "Menos um")
            }
            Text("${item.quantity}", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onIncrease) {
                Icon(Icons.Filled.Add, contentDescription = "Mais um")
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remover",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
