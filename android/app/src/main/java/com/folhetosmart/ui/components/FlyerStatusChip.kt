package com.folhetosmart.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.folhetosmart.ui.theme.ErrorRed
import com.folhetosmart.ui.theme.FolhetoSmartGreen
import com.folhetosmart.ui.theme.PromotionBadge
import com.folhetosmart.ui.theme.SavingsBadge
import com.folhetosmart.ui.theme.WaitingGrey

/**
 * Linha de estado de um supermercado no ecrã Sincronizar — 4 estados:
 *  1. pending + folheto indisponível → ⏳ A aguardar folheto (pulse);
 *  2. pending + folheto disponível   → ✅ Disponível (pronto a sincronizar);
 *  3. running                        → 🔄 A processar (loading);
 *  4. success                        → ✅ N produtos importados · hora (sem upload);
 *  5. error                          → ❌ Falhou + botão 📎 Carregar PDF.
 *
 * O botão de upload só aparece quando [syncStatus] == "error".
 */
@Composable
fun FlyerStatusChip(
    supermarketName: String,
    syncStatus: String,
    flyerAvailable: Boolean,
    availableSinceLabel: String?,
    productsImported: Int,
    syncedAtLabel: String?,
    modifier: Modifier = Modifier,
    syncSource: String? = null,
    progressMessage: String? = null,
    onUploadPdf: (() -> Unit)? = null
) {
    val pulse = rememberInfiniteTransition(label = "flyer-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )

    val background = when {
        syncStatus == "success" -> SavingsBadge
        syncStatus == "error" -> Color(0xFFFCE8E6)
        syncStatus == "running" -> BestPriceBg
        flyerAvailable -> SavingsBadge          // pending + disponível
        else -> Color(0xFFF1F1F1)               // pending + à espera
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LeadingIcon(syncStatus, flyerAvailable, pulseAlpha)

            Text(
                supermarketName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            TrailingContent(
                syncStatus = syncStatus,
                flyerAvailable = flyerAvailable,
                availableSinceLabel = availableSinceLabel,
                productsImported = productsImported,
                syncedAtLabel = syncedAtLabel,
                syncSource = syncSource,
                progressMessage = progressMessage,
                pulseAlpha = pulseAlpha
            )

            // Estado 4 — só em erro: botão de upload manual de PDF (Fix 3).
            if (syncStatus == "error" && onUploadPdf != null) {
                TextButton(onClick = onUploadPdf) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = "Carregar PDF",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(" PDF")
                }
            }
        }
    }
}

@Composable
private fun LeadingIcon(syncStatus: String, flyerAvailable: Boolean, pulseAlpha: Float) {
    when (syncStatus) {
        "running" -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = FolhetoSmartGreen
        )
        "success" -> Icon(
            Icons.Filled.CheckCircle, contentDescription = "Sincronizado",
            tint = FolhetoSmartGreen, modifier = Modifier.size(22.dp)
        )
        "error" -> Icon(
            Icons.Filled.ErrorOutline, contentDescription = "Falhou",
            tint = ErrorRed, modifier = Modifier.size(22.dp)
        )
        else -> if (flyerAvailable) {
            Icon(
                Icons.Filled.CheckCircle, contentDescription = "Disponível",
                tint = FolhetoSmartGreen, modifier = Modifier.size(22.dp)
            )
        } else {
            Icon(
                Icons.Filled.HourglassEmpty, contentDescription = "A aguardar",
                tint = WaitingGrey, modifier = Modifier.size(22.dp).alpha(pulseAlpha)
            )
        }
    }
}

@Composable
private fun TrailingContent(
    syncStatus: String,
    flyerAvailable: Boolean,
    availableSinceLabel: String?,
    productsImported: Int,
    syncedAtLabel: String?,
    syncSource: String?,
    progressMessage: String?,
    pulseAlpha: Float
) {
    when (syncStatus) {
        "running" -> Text(
            // progress_message do backend (ex.: "página 2/4") quando existe;
            // senão, fonte Drive -> "📁 Google Drive · A processar…".
            progressMessage
                ?: if (syncSource == "drive") "📁 Google Drive · A processar…" else "A processar…",
            style = MaterialTheme.typography.labelMedium,
            color = FolhetoSmartGreen,
            fontWeight = FontWeight.SemiBold
        )

        "success" -> Column(horizontalAlignment = Alignment.End) {
            Text(
                "$productsImported produtos",
                style = MaterialTheme.typography.labelMedium,
                color = FolhetoSmartGreen,
                fontWeight = FontWeight.SemiBold
            )
            if (!syncedAtLabel.isNullOrBlank()) {
                Text(
                    syncedAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        "error" -> Text(
            "Falhou",
            style = MaterialTheme.typography.labelMedium,
            color = ErrorRed,
            fontWeight = FontWeight.SemiBold
        )

        else -> if (flyerAvailable) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Disponível",
                    style = MaterialTheme.typography.labelMedium,
                    color = FolhetoSmartGreen,
                    fontWeight = FontWeight.SemiBold
                )
                if (!availableSinceLabel.isNullOrBlank()) {
                    Text(
                        availableSinceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                "A aguardar…",
                style = MaterialTheme.typography.labelMedium,
                color = WaitingGrey,
                modifier = Modifier.alpha(pulseAlpha)
            )
        }
    }
}

// Azul-claro suave para o estado "a processar".
private val BestPriceBg = Color(0xFFE3F2FD)
