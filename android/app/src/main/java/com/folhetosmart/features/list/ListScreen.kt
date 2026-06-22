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
import androidx.compose.material3.CircularProgressIndicator
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
import com.folhetosmart.ui.components.SavingsSummaryCard
import com.folhetosmart.ui.theme.FolhetoSmartGreen

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

            // Resumo da otimização
            state.result?.let { result ->
                item(key = "savings") {
                    Column {
                        if (state.resultFromCache) {
                            Text(
                                "📡 Sem ligação — última otimização guardada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        SavingsSummaryCard(result = result, itemCount = items.sumOf { it.quantity })
                    }
                }
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

        // Botão de otimizar
        Button(
            onClick = viewModel::optimize,
            enabled = items.isNotEmpty() && !state.optimizing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (state.optimizing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text("A otimizar…")
            } else {
                Text("💰 Otimizar por supermercado")
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
