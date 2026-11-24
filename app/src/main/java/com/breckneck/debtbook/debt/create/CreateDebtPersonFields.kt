package com.breckneck.debtbook.debt.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.*
import com.breckneck.debtbook.core.ui.theme.bubbleColors
import com.breckneck.debtbook.debt.PersonAvatar
import com.breckneck.debtbook.debt.encodePersonPhoto
import com.breckneck.debtbook.debt.rememberDebtPhotoPicker
import com.breckneck.debtbook.finance.financeMoney
import com.breckneck.deptbook.domain.model.HumanDomain

@Composable
internal fun CreateDebtPersonFields(
    vm: CreateDebtViewModel,
    fixedPerson: Boolean,
    name: TextFieldState,
    selectedPerson: HumanDomain?,
    nameMissing: Boolean,
    nameRequester: BringIntoViewRequester,
    onContactClick: () -> Unit,
) {
    val colors = bubbleColors()
    val focus = LocalFocusManager.current
    val context = LocalContext.current.applicationContext
    val people by vm.availablePeople.observeAsState(emptyList())
    val selectedId by vm.selectedPersonId.observeAsState()
    val currency by vm.currency.observeAsState("")
    val saving by vm.isSaving.observeAsState(false)
    val loadingPhoto by vm.loadingPhoto.observeAsState(false)
    val photo by vm.draftPhoto.observeAsState()
    val photoError by vm.photoError.observeAsState(false)
    val needsChoice by vm.needsPersonChoice.observeAsState(false)
    val pickedContact by vm.pickedContact.observeAsState()

    LaunchedEffect(pickedContact) {
        pickedContact?.let {
            vm.selectPerson(null)
            name.setTextAndPlaceCursorAtEnd(it)
            vm.pickedContact.value = null
            nameRequester.bringIntoView()
        }
    }
    LaunchedEffect(needsChoice) {
        if (needsChoice) nameRequester.bringIntoView()
    }

    if (fixedPerson) {
        BubbleValueRow(stringResource(R.string.person_name), name.text.toString())
        return
    }

    val query = name.text.toString().trim()
    val matches = remember(people, query, currency, selectedId) {
        if (selectedId != null || query.isBlank()) emptyList()
        else {
            val matching = people.filter { it.name.contains(query, ignoreCase = true) }
            val exact = matching.filter { it.name.trim().equals(query, ignoreCase = true) }
            exact.sortedBy { it.currency != currency } + matching.filterNot { it in exact }
                .sortedWith(compareBy({ !it.name.startsWith(query, ignoreCase = true) }, { it.currency != currency }, { it.name }))
                .take((5 - exact.size).coerceAtLeast(0))
        }
    }
    val canAddPhoto = selectedId == null && matches.none { it.currency == currency && it.name.trim().equals(query, ignoreCase = true) }
    val choosePhoto = rememberDebtPhotoPicker(onFailure = { vm.photoError.value = true }) { uri ->
        vm.setPhoto { encodePersonPhoto(context, uri) }
    }
    AnimatedContent(
        targetState = selectedId != null,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = { (fadeIn(tween(190)) togetherWith fadeOut(tween(140))).using(SizeTransform()) },
        label = "Debt person selection",
    ) { selected ->
        if (selected) {
            BubbleFieldRow(
                Icons.Outlined.Person,
                stringResource(R.string.debt_selected_person),
                trailing = {
                    IconButton({ vm.selectPerson(null) }, enabled = !saving) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.debt_change_person))
                    }
                },
            ) { Text(selectedPerson?.name ?: name.text.toString(), style = MaterialTheme.typography.bodyLarge) }
        } else {
            Column(Modifier.fillMaxWidth()) {
                BubbleTextFieldRow(
                    Icons.Outlined.Person, stringResource(R.string.person_name), name,
                    stringResource(R.string.debt_name_hint), !saving,
                    when {
                        nameMissing -> stringResource(R.string.debt_name_required)
                        needsChoice -> stringResource(R.string.debt_choose_matching_person)
                        else -> null
                    },
                    { true }, { vm.selectPerson(null) }, Modifier.testTag("humanNameEditText").bringIntoViewRequester(nameRequester),
                    trailing = {
                        TextButton(
                            onClick = { focus.clearFocus(); onContactClick() },
                            enabled = !saving,
                            modifier = Modifier.testTag("pickDebtContact"),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) { Text(stringResource(R.string.debt_contacts_short), style = MaterialTheme.typography.labelMedium) }
                    },
                )
                AnimatedContent(
                    targetState = matches,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween(210)) togetherWith fadeOut(tween(130))).using(
                            SizeTransform { _, _ -> tween(240) },
                        )
                    },
                    label = "Debt person suggestions",
                ) { displayed ->
                    if (displayed.isNotEmpty()) {
                        DebtPersonChoices(displayed, needsChoice, !saving && selectedId == null) { person ->
                            name.setTextAndPlaceCursorAtEnd(person.name)
                            vm.selectPerson(person.id)
                            focus.clearFocus()
                        }
                    }
                }
                AnimatedVisibility(canAddPhoto) {
                    Column {
                        HorizontalDivider(color = colors.border)
                        BubbleListRow(
                            Modifier.clickable(enabled = !saving && !loadingPhoto, role = Role.Button) {
                                focus.clearFocus()
                                choosePhoto()
                            }.testTag("pickDebtPhoto").heightIn(min = 52.dp).padding(start = 14.dp, end = 8.dp),
                            leading = {
                                if (photo != null) PersonAvatar(1, photo, 34.dp)
                                else Icon(Icons.Outlined.AddAPhoto, null, Modifier.padding(6.dp), tint = colors.muted)
                            },
                            trailing = if (photo == null) null else {
                                {
                                    IconButton({ vm.setPhoto { null } }, enabled = !saving && !loadingPhoto) {
                                        Icon(Icons.Outlined.Close, stringResource(R.string.debt_remove_photo))
                                    }
                                }
                            },
                        ) {
                            Text(
                                stringResource(if (photo == null) R.string.debt_choose_photo else R.string.debt_replace_photo),
                                color = colors.action,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (loadingPhoto) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (photoError) Text(
                            stringResource(R.string.debt_photo_failed),
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            color = colors.danger,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtPersonChoices(
    people: List<HumanDomain>,
    needsChoice: Boolean,
    enabled: Boolean,
    onSelect: (HumanDomain) -> Unit,
) {
    val colors = bubbleColors()
    Surface(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp).testTag("debtPersonSuggestions"),
        shape = MaterialTheme.shapes.large,
        color = if (needsChoice) colors.dangerContainer else colors.mint,
        contentColor = if (needsChoice) colors.danger else colors.onMint,
        border = BorderStroke(1.dp, if (needsChoice) colors.danger else colors.border),
    ) {
        Column {
            Text(
                stringResource(R.string.debt_existing_people),
                Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 4.dp)
                    .semantics { if (needsChoice) liveRegion = LiveRegionMode.Polite },
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
            for (person in people) key(person.id) {
                HorizontalDivider(color = colors.border)
                BubbleListRow(
                    Modifier.clickable(enabled = enabled, role = Role.Button) { onSelect(person) }
                        .testTag("debtPerson-${person.id}").heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                    leading = { PersonAvatar(person.id, person.photo, 32.dp) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(person.sumDebt.financeMoney(person.currency), style = MaterialTheme.typography.bodySmall)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
                        }
                    },
                ) {
                    Text(person.name, style = MaterialTheme.typography.titleSmall)
                    Text(person.currency, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
