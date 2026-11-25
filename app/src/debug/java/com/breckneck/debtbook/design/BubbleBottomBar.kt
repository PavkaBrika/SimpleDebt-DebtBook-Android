package com.breckneck.debtbook.design

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.breckneck.debtbook.core.navigation.MainTab
import com.breckneck.debtbook.core.ui.components.BubbleBottomBar
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme

@Preview(name = "Навигация · день", widthDp = 390, locale = "ru", showBackground = true, backgroundColor = 0xFFF6F7EF)
@Preview(name = "Навигация · ночь", widthDp = 390, locale = "ru", showBackground = true, backgroundColor = 0xFF19251E, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Навигация · узкий экран", widthDp = 320, fontScale = 1.3f, locale = "ru", showBackground = true)
@Composable
private fun BubbleBottomBarPreview() {
    var destination by remember { mutableStateOf(MainTab.DEBTS) }
    DebtBookTheme {
        BubbleBottomBar(destination, onDestinationSelected = { destination = it })
    }
}
