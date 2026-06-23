package com.folhetosmart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

// Avatar determinístico (estilo "identicon" do GitHub): a partir de uma semente
// estável (ex.: o email), escolhe sempre o mesmo animal + cor para o utilizador.
private val AVATAR_ANIMAIS = listOf(
    "🦊", "🐱", "🐶", "🐼", "🐨", "🦁", "🐯", "🐸", "🐵", "🦉", "🦅", "🐧",
    "🐢", "🐙", "🦋", "🐝", "🦄", "🐬", "🐳", "🦎", "🦒", "🦓", "🐰", "🐻",
    "🐷", "🐮", "🦔", "🦦", "🐹", "🦫"
)

private val AVATAR_CORES = listOf(
    0xFFEF9A9A, 0xFFF48FB1, 0xFFCE93D8, 0xFF9FA8DA, 0xFF90CAF9, 0xFF80DEEA,
    0xFFA5D6A7, 0xFFFFCC80, 0xFFBCAAA4, 0xFFB0BEC5, 0xFF80CBC4, 0xFFFFAB91
)

/** (animal, cor de fundo) determinísticos para a semente dada. */
fun avatarFor(seed: String): Pair<String, Long> {
    val h = seed.hashCode()
    val animal = AVATAR_ANIMAIS[Math.floorMod(h, AVATAR_ANIMAIS.size)]
    val cor = AVATAR_CORES[Math.floorMod(h / 31, AVATAR_CORES.size)]
    return animal to cor
}

/** Círculo colorido com o animal do utilizador. Clicável (ex.: abrir edição). */
@Composable
fun UserAvatar(
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    onClick: (() -> Unit)? = null
) {
    val (animal, cor) = remember(seed) { avatarFor(seed) }
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .background(Color(cor))
    Box(
        modifier = if (onClick != null) base.clickable { onClick() } else base,
        contentAlignment = Alignment.Center
    ) {
        Text(animal, style = TextStyle(fontSize = (size.value * 0.5f).sp))
    }
}
