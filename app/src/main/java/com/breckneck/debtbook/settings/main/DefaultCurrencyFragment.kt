package com.breckneck.debtbook.settings.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleGroupedRow
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.components.currencyChoices
import com.breckneck.debtbook.core.ui.components.normalizeCurrencySearch
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.settings.SettingsChoiceRow
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

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
    var query by rememberSaveable { mutableStateOf("") }
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val common = stringArrayResource(R.array.currencies).toList()
    val options = remember(common, selectedCode, locale) { currencyChoices(common, selectedCode, locale) }
    val results = remember(options, query) {
        val normalizedQuery = query.normalizeCurrencySearch()
        options.filter { it.matches(normalizedQuery) }
    }
    val updateQuery: (String) -> Unit = {
        query = it
        listState.requestScrollToItem(0)
    }

    BubbleScaffold(
        hostInsets = hostInsets,
        topBar = { BubbleTopBar(stringResource(R.string.default_currency), onBack, hostInsets) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = updateQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_currency)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { updateQuery("") }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.clear_currency_search))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.paper,
                    unfocusedContainerColor = colors.paper,
                    focusedBorderColor = colors.action,
                    unfocusedBorderColor = colors.border,
                    focusedLabelColor = colors.action,
                    unfocusedLabelColor = colors.muted,
                ),
            )
            if (results.isEmpty()) {
                BubbleGroup {
                    Text(
                        stringResource(R.string.no_matching_currencies),
                        modifier = Modifier.padding(16.dp),
                        color = colors.muted,
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.selectableGroup()) {
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
    }
}
