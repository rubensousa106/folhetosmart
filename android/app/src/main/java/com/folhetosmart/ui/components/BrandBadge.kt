package com.folhetosmart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.folhetosmart.ui.theme.FolhetoSmartGreen

/**
 * Emblema da marca — quadrado verde com um ícone de etiqueta (o mesmo espírito
 * do logótipo novo "etiquetas a comparar" do site/ícone da app). Usado nos
 * momentos de maior visibilidade (Login, Registo) em vez do antigo emoji de
 * carrinho de compras, que não dizia nada sobre o que a app faz.
 */
@Composable
fun BrandBadge(size: Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.29f))
            .background(FolhetoSmartGreen),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Sell,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
