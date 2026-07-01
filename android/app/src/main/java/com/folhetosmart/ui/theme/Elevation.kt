package com.folhetosmart.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de elevação partilhada para `Card` — antes os cards eram quase
 * planos (1.dp por omissão do Material3) em todos os ecrãs. Usar estes
 * valores em vez de números soltos por ecrã, para a profundidade ficar
 * consistente em toda a app.
 */
object FolhetoElevation {
    /** Cards normais (listas, formulários). */
    val card: Dp = 2.dp

    /** Cards de destaque — ex.: a oferta "mais barata" no Comparar. */
    val cardHighlighted: Dp = 6.dp
}
