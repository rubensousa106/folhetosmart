package com.folhetosmart.features.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folhetosmart.data.models.FlyerOfferingDto
import com.folhetosmart.ui.components.EmptyView
import com.folhetosmart.ui.components.ErrorView
import com.folhetosmart.ui.components.LoadingView
import com.folhetosmart.ui.theme.FolhetoSmartGreen

/**
 * Ecrã Comparar: montra de TODOS os produtos dos folhetos. Pesquisa por nome;
 * cada produto mostra as suas ofertas, com a MAIS BARATA em destaque (fundo +
 * ⭐). Carrinho ou deslizar para a esquerda adiciona à Lista de compras.
 */
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

    Box(Modifier.fillMaxSize()) {
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
                is CompareUiState.Loading -> LoadingView("A carregar produtos…")

                is CompareUiState.Error -> ErrorView(s.message, onRetry = viewModel::reload)

                is CompareUiState.Empty -> EmptyView(
                    emoji = if (s.query.isEmpty()) "🛒" else "🤷",
                    message = if (s.query.isEmpty())
                        "Ainda não há produtos.\nOs folhetos são atualizados às quintas."
                    else
                        "Sem resultados para “${s.query}”.\nExperimenta outro termo."
                )

                is CompareUiState.Content -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(s.groups, key = { it.produto }) { group ->
                        ProductGroupCard(group, onAdd = { viewModel.addToList(group.produto) })
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductGroupCard(group: ProductGroup, onAdd: () -> Unit) {
    // Deslizar para a esquerda dispara o "adicionar à lista" e volta ao sítio.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onAdd()
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(FolhetoSmartGreen)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.AddShoppingCart,
                    contentDescription = "Adicionar à lista",
                    tint = Color.White
                )
            }
        }
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.produto,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onAdd) {
                        Icon(
                            Icons.Filled.AddShoppingCart,
                            contentDescription = "Adicionar à lista",
                            tint = FolhetoSmartGreen
                        )
                    }
                }
                Spacer(Modifier.size(6.dp))
                group.offers.forEachIndexed { index, offer ->
                    OfferRow(offer, highlight = group.hasMultiple && index == 0)
                }
            }
        }
    }
}

/** Uma oferta (supermercado · validade · preço). A mais barata fica destacada. */
@Composable
private fun OfferRow(offer: FlyerOfferingDto, highlight: Boolean) {
    val background = if (highlight) FolhetoSmartGreen.copy(alpha = 0.14f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                offer.supermercado,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
            )
            // Nome tal como aparece no folheto, quando difere do canónico.
            offer.original?.takeIf { it.isNotBlank() && it != offer.produto }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            offer.validade?.let {
                Text(
                    "Válido: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "€${String.format("%.2f", offer.preco)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (highlight) FolhetoSmartGreen else MaterialTheme.colorScheme.onSurface
        )
        if (highlight) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Star,
                contentDescription = "Mais barato",
                tint = FolhetoSmartGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
