package com.folhetosmart.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

val Typography = Typography()

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
