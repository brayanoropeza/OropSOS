package com.rescue.sos.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun BannerAdView(
    isProUser: Boolean,
    isEmergencyActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Si el usuario es PRO / Donador O si hay una emergencia activa, ocultar anuncios completamente
    if (isProUser || isEmergencyActive) {
        return
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-7138362094230027/8167652634"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
