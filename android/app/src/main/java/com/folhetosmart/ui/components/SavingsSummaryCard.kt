package com.folhetosmart.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.folhetosmart.data.api.OptimizeResponseDto
import com.folhetosmart.ui.theme.FolhetoSmartGreen
import com.folhetosmart.ui.theme.SavingsBadge

/**
 * Resumo da poupança da lista (ecrã Lista):
 *  💰 Poupança desta semana / 8,43 € em 12 produtos / [Ver por supermercado ▼]
 */
@Composable
fun SavingsSummaryCard(
    result: OptimizeResponseDto,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SavingsBadge)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "💰 Poupança desta semana",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${Formatters.price(result.poupanca)} em $itemCount produtos",
                style = MaterialTheme.typography.headlineSmall,
                color = FolhetoSmartGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Total otimizado: ${Formatters.price(result.totalOtimizado)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ocultar detalhe" else "Ver por supermercado")
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    result.porSupermercado.forEach { basket ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "🏪 ${basket.supermarket}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                Formatters.price(basket.subtotal),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = FolhetoSmartGreen
                            )
                        }
                        basket.items.forEach { item ->
                            Row(Modifier.padding(start = 8.dp, top = 2.dp)) {
                                Text(
                                    "${item.quantity}× ${item.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    Formatters.price(item.lineTotal),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
