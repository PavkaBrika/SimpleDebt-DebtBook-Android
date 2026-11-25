package com.breckneck.debtbook.core.navigation

import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breckneck.debtbook.BuildConfig
import com.breckneck.debtbook.app.App
import com.breckneck.debtbook.core.MainActivity
import com.breckneck.debtbook.core.MainViewModel
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.compose.Banner
import com.yandex.mobile.ads.compose.BannerEvents
import com.yandex.mobile.ads.compose.BannerSize
import com.yandex.mobile.ads.compose.rememberBannerAdState

@Composable
internal fun MainBannerSlot(activity: MainActivity, main: MainViewModel, onSize: (Int) -> Unit) {
    val ready by (activity.application as App).adsReady.collectAsStateWithLifecycle()
    val disabled by main.adsDisabled.observeAsState(false)
    BoxWithConstraints(Modifier.fillMaxWidth().onSizeChanged { onSize(it.height) }) {
        if (ready && !disabled && maxWidth >= 1.dp) {
            key(maxWidth) {
                var loaded by remember { mutableStateOf(false) }
                val state = rememberBannerAdState(
                    adSize = BannerSize.Sticky(maxWidth),
                    events = BannerEvents(
                        onAdLoaded = {
                            loaded = true
                            Log.i("BANNER AD", "Banner loaded")
                        },
                        onAdFailedToLoad = { error ->
                            Log.e("BANNER AD", "Banner load failed: ${error.description}")
                        },
                        onAdClicked = { Log.i("BANNER AD", "Banner clicked") },
                        onImpression = { Log.i("BANNER AD", "Banner impression") },
                    ),
                )
                LaunchedEffect(state) {
                    val id = if (BuildConfig.DEMO_ADS) "demo-banner-yandex" else "R-M-1753297-1"
                    state.loadAd(AdRequest.Builder(id).build())
                }
                Banner(state, Modifier.layout { measurable, constraints ->
                    val banner = measurable.measure(constraints)
                    layout(banner.width, if (loaded) banner.height else 0) {
                        if (loaded) banner.placeRelative(0, 0)
                    }
                })
            }
        }
    }
}
