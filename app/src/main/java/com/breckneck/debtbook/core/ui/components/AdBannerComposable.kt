package com.breckneck.debtbook.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest

/**
 * Composable wrapper for Yandex BannerAdView via AndroidView.
 * Mirrors the setup in MainActivity for a sticky-width banner.
 */
@Composable
fun AdBannerComposable(
    adUnitId: String = "R-M-1753297-1",
    modifier: Modifier = Modifier
) {
    val widthDp = LocalWindowInfo.current.containerDpSize.width.value.toInt()
    if (widthDp <= 0) return
    key(adUnitId, widthDp) {
        AndroidView(
            factory = { context ->
                BannerAdView(context).apply {
                    setAdSize(BannerAdSize.sticky(context, widthDp))
                    loadAd(AdRequest.Builder(adUnitId).build())
                }
            },
            modifier = modifier,
            onRelease = { it.destroy() }
        )
    }
}

@Preview(name = "AdBanner — placeholder")
@Composable
private fun AdBannerPreview() {
    DebtBookTheme(dynamicColor = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ad Banner (320\u00d750)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
