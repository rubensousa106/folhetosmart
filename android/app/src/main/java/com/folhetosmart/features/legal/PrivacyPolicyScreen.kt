package com.folhetosmart.features.legal

import androidx.compose.runtime.Composable
import com.folhetosmart.R

/** Política de Privacidade — texto local (res/raw), sem internet. */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalDocumentScreen(
        title = "Política de Privacidade",
        rawRes = R.raw.privacy_policy,
        onBack = onBack
    )
}
