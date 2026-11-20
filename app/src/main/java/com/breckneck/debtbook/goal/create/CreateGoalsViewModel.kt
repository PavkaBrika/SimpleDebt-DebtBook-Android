package com.breckneck.debtbook.goal.create

import java.math.BigDecimal

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.breckneck.debtbook.R
import com.breckneck.debtbook.goal.create.mapper.toCreateGoalUi
import com.breckneck.debtbook.goal.create.mapper.toDomain
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.model.Goal
import com.breckneck.deptbook.domain.usecase.Goal.SetGoal
import com.breckneck.deptbook.domain.usecase.Goal.UpdateGoal
import com.breckneck.deptbook.domain.usecase.Settings.GetDefaultCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class CreateGoalsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val setGoal: SetGoal,
    private val updateGoal: UpdateGoal,
    private val getDefaultCurrency: GetDefaultCurrency,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), ContainerHost<CreateGoalsState, CreateGoalsSideEffect> {

    private val TAG = "CreateGoalsViewModel"

    private var originalGoal: Goal? = null

    override val container = container<CreateGoalsState, CreateGoalsSideEffect>(
        initialState = CreateGoalsState.initial(),
        onCreate = {
            val names = context.resources.getStringArray(R.array.currencies).toList()
            val isEditMode: Boolean = savedStateHandle.get<Boolean>("isEditGoal") ?: false

            reduce { state.copy(currencyNames = names, isEditMode = isEditMode) }

            if (isEditMode) initEditMode() else initCreateMode()
        }
    )

    init {
        Log.d(TAG, "Created")
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "Cleared")
    }

    fun onAction(action: CreateGoalsAction) = when (action) {
        is CreateGoalsAction.NameChanged -> onNameChanged(action.value)
        is CreateGoalsAction.SumChanged -> onSumChanged(action.value)
        is CreateGoalsAction.SavedSumChanged -> onSavedSumChanged(action.value)
        CreateGoalsAction.CurrencyClick -> onCurrencyClick()
        is CreateGoalsAction.CurrencySelected -> onCurrencySelected(action.index)
        CreateGoalsAction.DismissCurrencySheet -> onDismissCurrencySheet()
        CreateGoalsAction.DateClick -> onDateClick()
        CreateGoalsAction.DismissDatePicker -> onDismissDatePicker()
        is CreateGoalsAction.DateSelected -> onDateSelected(action.date)
        is CreateGoalsAction.ImagePicked -> onImagePicked(action.uri)
        CreateGoalsAction.DeleteImage -> onDeleteImage()
        CreateGoalsAction.PhotoCardClick -> onPhotoCardClick()
        CreateGoalsAction.SaveClick -> onSaveClick()
        CreateGoalsAction.BackClick -> onBackClick()
    }

    private fun initEditMode() = intent {
        val goal = savedStateHandle.get<Goal>("goal") ?: run {
            postSideEffect(CreateGoalsSideEffect.NavigateBack())
            return@intent
        }

        originalGoal = goal
        reduce {
            state.copy(
                goal = goal.toCreateGoalUi(currencyDisplayNameFor(goal.currency)),
                selectedCurrencyIndex = currencyIndexOf(goal.currency),
            )
        }
    }

    private fun initCreateMode() = intent {
        val defaultCurrency = getDefaultCurrency.execute()
        reduce {
            state.copy(
                goal = state.goal.copy(
                    currency = defaultCurrency,
                    currencyDisplayName = currencyDisplayNameFor(defaultCurrency),
                ),
                selectedCurrencyIndex = currencyIndexOf(defaultCurrency),
            )
        }
    }

    private fun onNameChanged(value: String) = intent {
        reduce { state.copy(goal = state.goal.copy(name = value), nameError = null) }
    }

    private fun onSumChanged(value: String) = intent {
        if (DecimalInput.isEditable(value)) {
            reduce { state.copy(goal = state.goal.copy(sum = value), sumError = null) }
        }
    }

    private fun onSavedSumChanged(value: String) = intent {
        if (DecimalInput.isEditable(value)) {
            reduce { state.copy(goal = state.goal.copy(savedSum = value), savedSumError = null) }
        }
    }

    private fun onCurrencyClick() = intent {
        reduce { state.copy(isCurrencySheetVisible = true) }
    }

    private fun onCurrencySelected(index: Int) = intent {
        val displayName = state.currencyNames.getOrNull(index) ?: return@intent
        val symbol = displayName.substringAfterLast(" ")
        reduce {
            state.copy(
                goal = state.goal.copy(
                    currency = symbol,
                    currencyDisplayName = displayName,
                ),
                selectedCurrencyIndex = index,
                isCurrencySheetVisible = false,
            )
        }
    }

    private fun onDismissCurrencySheet() = intent {
        reduce { state.copy(isCurrencySheetVisible = false) }
    }

    private fun onDateClick() = intent {
        reduce { state.copy(isDatePickerVisible = true) }
    }

    private fun onDismissDatePicker() = intent {
        reduce { state.copy(isDatePickerVisible = false) }
    }

    private fun onDateSelected(date: Date) = intent {
        reduce {
            state.copy(
                goal = state.goal.copy(goalDate = date),
                isDatePickerVisible = false,
            )
        }
    }

    private fun onImagePicked(uri: Uri) = intent {
        reduce {
            state.copy(
                goal = state.goal.copy(
                    imageUri = uri,
                    imagePath = null,
                ),
            )
        }
    }

    private fun onDeleteImage() = intent {
        reduce {
            state.copy(
                goal = state.goal.copy(
                    imageUri = null,
                    imagePath = null,
                ),
            )
        }
    }

    private fun onPhotoCardClick() = intent {
        postSideEffect(CreateGoalsSideEffect.LaunchImagePicker)
    }

    private fun onSaveClick() = intent {
        if (state.isSaving) return@intent
        val goal = state.goal
        val name = goal.name.trim()
        val sumText = goal.sum.trim()
        val savedSumText = goal.savedSum.trim()

        val nameError = CreateGoalsFormValidation.validateName(name)
        val (sumDouble, sumError) = CreateGoalsFormValidation.validateSum(sumText, goal.currency, originalGoal?.sum)
        val (savedSumDouble, savedSumError) =
            CreateGoalsFormValidation.validateSavedSum(savedSumText, sumDouble, goal.currency, originalGoal?.savedSum)

        if (nameError != null || sumError != null || savedSumError != null) {
            reduce {
                state.copy(
                    nameError = nameError,
                    sumError = sumError,
                    savedSumError = savedSumError
                )
            }
            return@intent
        }

        reduce {
            state.copy(nameError = null, sumError = null, savedSumError = null,
                isSaving = true, saveFailed = false)
        }
        val isEdit = state.isEditMode
        try {
            val savedGoal = withContext(Dispatchers.IO) {
                val original = if (isEdit) checkNotNull(originalGoal) else null
                val photoPath = if (isEdit && goal.imagePath != null) original?.photoPath
                else savePhotoToInternalStorage(goal.imageUri)
                val savingGoal = goal.toDomain(
                    sum = sumDouble!!,
                    savedSum = savedSumDouble ?: BigDecimal.ZERO,
                    photoPath = photoPath,
                    originalGoal = original,
                )
                if (isEdit) updateGoal.execute(savingGoal) else setGoal.execute(savingGoal)
                savingGoal
            }
            // Keep the save guard until this screen is removed, including rapid repeated taps.
            postSideEffect(CreateGoalsSideEffect.NavigateBack(
                editedGoal = savedGoal.takeIf { isEdit }, saved = true,
            ))
        } catch (cancelled: CancellationException) {
            reduce { state.copy(isSaving = false) }
            throw cancelled
        } catch (_: Exception) {
            reduce { state.copy(isSaving = false, saveFailed = true) }
        }
    }

    private fun onBackClick() = intent {
        postSideEffect(CreateGoalsSideEffect.NavigateBack())
    }

    private fun savePhotoToInternalStorage(uri: Uri?): String? {
        if (uri == null) return null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "${uri.pathSegments.last()}.jpg"
            val outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            "${context.filesDir.absolutePath}/$fileName"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save photo: ${e.message}")
            null
        }
    }

    private fun currencyDisplayNameFor(currency: String): String =
        //todo fix this - remove currencyNames. Store currency in ENUM.
        container.stateFlow.value.currencyNames.firstOrNull { it.contains(currency) } ?: currency

    private fun currencyIndexOf(currency: String): Int =
        //todo fix this - remove currencyNames. Store currency in ENUM.
        container.stateFlow.value.currencyNames.indexOfFirst { it.contains(currency) }
            .coerceAtLeast(0)

}
