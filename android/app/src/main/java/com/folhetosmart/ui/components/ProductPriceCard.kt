package com.folhetosmart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.folhetosmart.data.api.ProductPriceDto
import com.folhetosmart.ui.theme.BestPriceBadge
import com.folhetosmart.ui.theme.BestPriceText
import com.folhetosmart.ui.theme.FolhetoSmartTheme
import com.folhetosmart.ui.theme.OriginalPriceStyle
import com.folhetosmart.ui.theme.PriceTextStyle
import com.folhetosmart.ui.theme.PromotionBadge
import com.folhetosmart.ui.theme.PromotionText

/**
 * Cartão de preço por supermercado (ecrã Comparar):
 *  🏪 Lidl [MELHOR] / 1,39 € ~~1,99~~ [-30%] / Válido até domingo
 */
@Composable
fun ProductPriceCard(price: ProductPriceDto, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🏪 ${price.supermarket}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (price.isBestPrice) {
                    Badge(text = "MELHOR", background = BestPriceBadge, contentColor = BestPriceText)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(Formatters.price(price.price), style = PriceTextStyle)

                if (price.originalPrice != null && price.originalPrice > price.price) {
                    Text(Formatters.price(price.originalPrice), style = OriginalPriceStyle)
                }
                if (price.isPromotion && !price.promotionLabel.isNullOrBlank()) {
                    Badge(
                        text = price.promotionLabel,
                        background = PromotionBadge,
                        contentColor = PromotionText
                    )
                }
            }

            val validity = Formatters.validUntil(price.validUntil)
            if (validity.isNotBlank()) {
                Text(
                    validity,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun Badge(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun ProductPriceCardPreview() {
    FolhetoSmartTheme {
        ProductPriceCard(
            ProductPriceDto(
                supermarket = "Lidl",
                supermarketSlug = "lidl",
                price = 1.39,
                originalPrice = 1.99,
                isPromotion = true,
                promotionLabel = "-30%",
                validUntil = "2026-06-14",
                isBestPrice = true
            )
        )
    }
}
