package com.breckneck.debtbook.core.currency

import com.breckneck.debtbook.core.navigation.RouteBackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleGroupedRow
import com.breckneck.debtbook.core.ui.components.BubbleIconButton
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleSearchField
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.components.currencyChoices
import com.breckneck.debtbook.core.ui.components.normalizeCurrencySearch
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.SettingsChoiceRow
import java.util.Locale
import util.DeviceCurrency

@Composable
internal fun CurrencyPickerScreen(
    title: String,
    selectedCode: String,
    hostInsets: PaddingValues,
    onBack: () -> Unit,
    onCurrencySelected: (String) -> Unit,
    availableCodes: List<String>? = null,
) {
    val colors = bubbleColors()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
    val regionalCurrency = remember(context, configuration) { DeviceCurrency.get(context) }
    val common = stringArrayResource(R.array.currencies).toList()
    val options = remember(common, selectedCode, regionalCurrency, locale, availableCodes) {
        currencyChoices(common, selectedCode, regionalCurrency, locale, availableCodes)
    }
    val results = remember(options, query) {
        val normalizedQuery = query.normalizeCurrencySearch()
        options.filter { it.matches(normalizedQuery) }
    }

    fun updateQuery(value: String) {
        query = value
        listState.requestScrollToItem(0)
    }

    fun closeSearch() {
        focusManager.clearFocus()
        if (query.isNotEmpty()) updateQuery("")
        searching = false
    }

    RouteBackHandler(enabled = searching, onBack = ::closeSearch)

    BubbleScaffold(
        hostInsets = hostInsets,
        scrollBehindBarsWithIme = true,
        topBar = {
            BubbleTopBar(
                title = title,
                onBack = onBack,
                hostInsets = hostInsets,
                titleContent = if (searching) {
                    {
                        BubbleSearchField(
                            label = stringResource(R.string.search_currency),
                            query = query,
                            onQueryChange = ::updateQuery,
                            onClearOrClose = { if (query.isEmpty()) closeSearch() else updateQuery("") },
                        )
                    }
                } else null,
                action = { diameter ->
                    if (!searching) {
                        BubbleIconButton(
                            onClick = { searching = true },
                            diameter = diameter,
                            containerColor = colors.headerBubble,
                        ) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.search_currency))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).selectableGroup(),
            state = listState,
            contentPadding = padding,
        ) {
            if (results.isEmpty()) {
                item {
                    BubbleGroup {
                        Text(
                            stringResource(R.string.no_matching_currencies),
                            modifier = Modifier.padding(16.dp),
                            color = colors.muted,
                        )
                    }
                }
            }
            itemsIndexed(results, key = { _, option -> option.code }) { index, option ->
                BubbleGroupedRow(index, results.size) {
                    SettingsChoiceRow(option.label, selected = option.code == selectedCode) {
                        focusManager.clearFocus()
                        onCurrencySelected(option.code)
                    }
                }
            }
        }
    }
}
