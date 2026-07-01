package com.folhetosmart.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Hierarquia de títulos mais marcada (mais peso, traço mais apertado) — o
 * resto (corpo, legendas) fica nos valores por omissão do Material3, que já
 * são legíveis. Mantém a família tipográfica do sistema (Roboto); só ajusta
 * peso/espaçamento, nunca tamanhos soltos nos ecrãs (ver MaterialTheme.typography).
 */
private val defaultTypography = Typography()

val Typography = defaultTypography.copy(
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontWeight = FontWeight.Bold
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold
    )
)

// Tipografia clara para preços e comparações
val PriceTextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    color = FolhetoSmartGreen
)

val OriginalPriceStyle = TextStyle(
    textDecoration = TextDecoration.LineThrough,
    color = Color.Gray,
    fontSize = 14.sp
)

val BadgeTextStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp
)
