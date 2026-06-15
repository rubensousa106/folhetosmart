package com.folhetosmart.features.legal

import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Layout partilhado dos documentos legais: botão "Voltar" no topo e texto
 * com scroll, carregado de res/raw (funciona sem internet).
 *
 * É um composable autónomo (sem dependência do NavController) para poder ser
 * usado tanto como rota do NavGraph (Definições) como sobreposto no
 * Onboarding, antes de a navegação existir.
 */
@Composable
fun LegalDocumentScreen(
    title: String,
    @RawRes rawRes: Int,
    onBack: () -> Unit
) {
    // O botão "retroceder" do sistema também fecha o documento.
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val text = remember(rawRes) {
        context.resources.openRawResource(rawRes)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    // Surface opaco: também é usado como overlay por cima do Onboarding.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar"
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider()

            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            )
        }
    }
}
