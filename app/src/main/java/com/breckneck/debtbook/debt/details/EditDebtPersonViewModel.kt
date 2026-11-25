package com.breckneck.debtbook.debt.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breckneck.deptbook.domain.model.HumanDomain
import com.breckneck.deptbook.domain.repository.DataTransaction
import com.breckneck.deptbook.domain.repository.HumanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class EditDebtPersonState(
    val person: HumanDomain? = null,
    val photo: String? = null,
    val loading: Boolean = true,
    val readingPhoto: Boolean = false,
    val saving: Boolean = false,
    val photoFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
internal class EditDebtPersonViewModel @Inject constructor(
    private val people: HumanRepository,
    private val transaction: DataTransaction,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val id = requireNotNull(savedState.get<Int>("idHuman"))
    private val _state = MutableStateFlow(EditDebtPersonState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val person = withContext(Dispatchers.IO) { requireNotNull(people.getHuman(id)) }
                val photo = if (savedState.contains("editedPersonPhoto")) savedState.get<String>("editedPersonPhoto") else person.photo
                _state.update { it.copy(person = person, photo = photo) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(person = null) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun showPhotoError() { _state.update { it.copy(photoFailed = true) } }

    fun changePhoto(read: suspend () -> String?) {
        if (state.value.readingPhoto || state.value.saving) return
        _state.update { it.copy(readingPhoto = true, photoFailed = false) }
        viewModelScope.launch {
            try {
                val photo = read()
                savedState["editedPersonPhoto"] = photo
                _state.update { it.copy(photo = photo) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showPhotoError()
            } finally {
                _state.update { it.copy(readingPhoto = false) }
            }
        }
    }

    fun save(name: String) {
        val draft = state.value
        if (draft.person == null || draft.saving || draft.readingPhoto || draft.saved || name.isBlank()) return
        val photoEdited = savedState.contains("editedPersonPhoto")
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    transaction.run {
                        val current = requireNotNull(people.getHuman(id))
                        people.updateHuman(current.copy(name = name.trim(), photo = if (photoEdited) draft.photo else current.photo))
                    }
                }
                _state.update { it.copy(saved = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(saveFailed = true) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
