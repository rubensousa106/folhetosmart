package com.folhetosmart.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folhetosmart.ui.viewmodels.ProductViewModel

@Composable
fun ProductScreen(
    viewModel: ProductViewModel,
    supermarket: String
) {
    val products by viewModel.products.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // Carrega os produtos quando a tela é aberta
    LaunchedEffect(Unit) {
        viewModel.loadProducts(supermarket)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator()
            }
            error != null -> {
                Text(
                    text = "❌ Erro: $error",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
            products.isEmpty() -> {
                Text(
                    text = "📭 Sem produtos disponíveis para $supermarket",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(16.dp)
                ) {
                    items(products) { product ->
                        Text(
                            text = "• ${product.produto}: €${String.format("%.2f", product.preco)}",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
