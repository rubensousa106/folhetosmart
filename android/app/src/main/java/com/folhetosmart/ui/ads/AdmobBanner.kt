package com.folhetosmart.ui.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

// ID de TESTE do AdMob (banner). TROCAR pelo bloco de anúncio real (criado na
// consola do AdMob) antes de publicar — só os IDs reais geram receita.
private const val TEST_BANNER_AD_UNIT = "ca-app-pub-3940256099942544/6300978111"

/**
 * Banner do AdMob para embeber no Compose (via AndroidView). É usado apenas para
 * utilizadores USER — o ecrã que o coloca decide se o mostra (o ADMIN nunca vê).
 */
@Composable
fun AdmobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = TEST_BANNER_AD_UNIT,
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
