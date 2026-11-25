package com.breckneck.debtbook.debt.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.bubbleScreen
import com.breckneck.debtbook.core.ui.components.BubbleButton
import com.breckneck.debtbook.core.ui.components.BubbleFormScroll
import com.breckneck.debtbook.core.ui.components.BubbleGroup
import com.breckneck.debtbook.core.ui.components.BubbleOutlinedButton
import com.breckneck.debtbook.core.ui.components.BubbleScaffold
import com.breckneck.debtbook.core.ui.components.BubbleTextFieldRow
import com.breckneck.debtbook.core.ui.components.BubbleTopBar
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.PersonAvatar
import com.breckneck.debtbook.debt.encodePersonPhoto
import com.breckneck.debtbook.debt.rememberDebtPhotoPicker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditDebtPersonFragment : Fragment() {
    private val vm by viewModels<EditDebtPersonViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        bubbleScreen(R.id.editDebtPersonFragment) { insets ->
            EditDebtPersonScreen(vm, insets, { findNavController().popBackStack() }) {
                setFragmentResult(DEBT_DETAILS_RESULT, Bundle())
                findNavController().popBackStack()
            }
        }
}

@Composable
private fun EditDebtPersonScreen(vm: EditDebtPersonViewModel, insets: PaddingValues, onBack: () -> Unit, onSaved: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.saving || state.saved) {}
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    val person = state.person
    if (person == null) {
        BubbleScaffold(insets, topBar = { BubbleTopBar(stringResource(R.string.person_edit_title), onBack, insets) }) { padding ->
            if (state.loading) CircularProgressIndicator(Modifier.padding(padding))
            else BubbleOutlinedButton(vm::load, Modifier.padding(padding)) { Text(stringResource(R.string.history_retry)) }
        }
        return
    }

    val name = rememberTextFieldState(person.name)
    val context = LocalContext.current.applicationContext
    val focus = LocalFocusManager.current
    val colors = bubbleColors()
    val busy = state.saving || state.saved || state.readingPhoto
    val pickPhoto = rememberDebtPhotoPicker(vm::showPhotoError) { uri -> vm.changePhoto { encodePersonPhoto(context, uri) } }
    BubbleScaffold(
        insets,
        topBar = { BubbleTopBar(stringResource(R.string.person_edit_title), onBack, insets, backEnabled = !state.saving) },
        bottomAction = {
            if (state.saveFailed) Text(stringResource(R.string.person_edit_failed),
                Modifier.padding(horizontal = 16.dp), color = colors.danger)
            BubbleButton({ focus.clearFocus(); vm.save(name.text.toString()) },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("saveDebtPerson"),
                enabled = !busy && name.text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        bottomBarScrim = false,
    ) { padding ->
        BubbleFormScroll(padding, Modifier.testTag("editDebtPerson"), rememberScrollState()) {
            BubbleGroup {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersonAvatar(person.id, state.photo, size = 80.dp)
                    BubbleOutlinedButton({ focus.clearFocus(); pickPhoto() }, Modifier.testTag("editDebtPersonPhoto"), enabled = !busy) {
                        Text(stringResource(if (state.photo == null) R.string.debt_choose_photo else R.string.debt_replace_photo))
                    }
                    if (state.photo != null) {
                        BubbleOutlinedButton({ vm.changePhoto { null } }, enabled = !busy) {
                            Text(stringResource(R.string.debt_remove_photo))
                        }
                    }
                    if (state.photoFailed) Text(stringResource(R.string.debt_photo_failed), color = colors.danger)
                }
                HorizontalDivider(color = colors.border)
                BubbleTextFieldRow(Icons.Outlined.Person, stringResource(R.string.person_name), name,
                    stringResource(R.string.person_name), !busy, null, { true }, {},
                    Modifier.testTag("editDebtPersonName"), KeyboardOptions(imeAction = ImeAction.Done))
            }
        }
    }
}
