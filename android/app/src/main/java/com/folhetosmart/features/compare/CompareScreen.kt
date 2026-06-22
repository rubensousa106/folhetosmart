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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
                        ProductGroupCard(group, onAdd = { offer ->
                            viewModel.addOffer(group.produto, offer.supermercado, offer.preco)
                        })
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

@Composable
private fun ProductGroupCard(group: ProductGroup, onAdd: (FlyerOfferingDto) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                group.produto,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(6.dp))
            // Uma linha por oferta — cada uma com o seu carrinho (escolhe a loja/preço).
            group.offers.forEachIndexed { index, offer ->
                OfferRow(
                    offer = offer,
                    highlight = group.hasMultiple && index == 0,
                    onAdd = { onAdd(offer) }
                )
            }
        }
    }
}

/** Uma oferta (marca · supermercado · validade · preço) + carrinho para a juntar à lista. */
@Composable
private fun OfferRow(offer: FlyerOfferingDto, highlight: Boolean, onAdd: () -> Unit) {
    val background = if (highlight) FolhetoSmartGreen.copy(alpha = 0.14f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            // Marca (só quando é marca nacional; vazio nas marcas-próprias da loja).
            offer.marca?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                offer.supermercado,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
            )
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
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.Star,
                contentDescription = "Mais barato",
                tint = FolhetoSmartGreen,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onAdd) {
            Icon(
                Icons.Filled.AddShoppingCart,
                contentDescription = "Adicionar à lista",
                tint = FolhetoSmartGreen
            )
        }
    }
}
