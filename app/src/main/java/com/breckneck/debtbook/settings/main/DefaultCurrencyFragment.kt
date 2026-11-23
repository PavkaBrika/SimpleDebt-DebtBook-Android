package com.breckneck.debtbook.settings.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleGroupedRow
import com.breckneck.debtbook.core.ui.components.BubbleIconButton
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.components.currencyChoices
import com.breckneck.debtbook.core.ui.components.normalizeCurrencySearch
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.SettingsChoiceRow
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import util.DeviceCurrency

@AndroidEntryPoint
class DefaultCurrencyFragment : Fragment() {
    private val vm by viewModels<SettingsViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val host = requireActivity() as SettingsFragment.OnButtonClickListener
        return bubbleScreen(R.id.defaultCurrencyComposeView) { insets ->
            val currency by vm.defaultCurrency.observeAsState("")
            DefaultCurrencyScreen(
                selectedCode = currency,
                hostInsets = insets,
                onBack = { finishSelection() },
                onCurrencySelected = { code ->
                    finishSelection {
                        vm.setDefaultCurrency(code)
                        host.onTickVibration()
                    }
                },
            )
        }
    }

    private fun finishSelection(save: () -> Unit = {}) {
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.defaultCurrencyFragment) return
        save()
        nav.popBackStack()
    }
}

@Composable
private fun DefaultCurrencyScreen(
    selectedCode: String,
    hostInsets: PaddingValues,
    onBack: () -> Unit,
    onCurrencySelected: (String) -> Unit,
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
    val options = remember(common, selectedCode, regionalCurrency, locale) {
        currencyChoices(common, selectedCode, regionalCurrency, locale)
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

    BackHandler(enabled = searching, onBack = ::closeSearch)

    BubbleScaffold(
        hostInsets = hostInsets,
        scrollBehindBarsWithIme = true,
        topBar = {
            BubbleTopBar(
                title = stringResource(R.string.default_currency),
                onBack = onBack,
                hostInsets = hostInsets,
                titleContent = if (searching) {
                    {
                        CurrencySearch(
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

@Composable
private fun CurrencySearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearOrClose: () -> Unit,
) {
    val colors = bubbleColors()
    val label = stringResource(R.string.search_currency)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f)
                .focusRequester(focusRequester)
                .semantics { contentDescription = label },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.action),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { editor ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            label,
                            Modifier.clearAndSetSemantics {},
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    editor()
                }
            },
        )
        IconButton(onClick = onClearOrClose) {
            val description = if (query.isEmpty()) R.string.close_currency_search else R.string.clear_currency_search
            Icon(Icons.Outlined.Close, stringResource(description), Modifier.size(20.dp))
        }
    }
}
