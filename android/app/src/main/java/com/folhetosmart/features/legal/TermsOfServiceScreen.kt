package com.folhetosmart.features.legal

import androidx.compose.runtime.Composable
import com.folhetosmart.R

/** Termos de Utilização — texto local (res/raw), sem internet. */
@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    LegalDocumentScreen(
        title = "Termos de Utilização",
        rawRes = R.raw.terms_of_service,
        onBack = onBack
    )
}
