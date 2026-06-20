package com.folhetosmart.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folhetosmart.ui.viewmodels.ProductViewModel

/**
 * Folheto de um supermercado: a lista de produtos da semana (modelo simples
 * {produto, preco}). Aberto a partir do ecrã Sincronizar ao tocar numa linha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    viewModel: ProductViewModel,
    supermarket: String,
    onBack: () -> Unit = {}
) {
    val products by viewModel.products.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // Recarrega sempre que muda o supermercado.
    LaunchedEffect(supermarket) {
        viewModel.loadProducts(supermarket)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("📋 $supermarket", fontWeight = FontWeight.Bold)
                        if (products.isNotEmpty()) {
                            Text(
                                "${products.size} produtos",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                error != null -> Text(
                    text = "❌ Erro: $error",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
                products.isEmpty() -> Text(
                    text = "📭 Sem produtos disponíveis para $supermarket",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
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
