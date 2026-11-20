package com.breckneck.debtbook.goal.create

import java.math.BigDecimal

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.breckneck.debtbook.R
import com.breckneck.debtbook.goal.create.mapper.toCreateGoalUi
import com.breckneck.debtbook.goal.create.mapper.toDomain
import com.breckneck.deptbook.domain.util.DecimalInput
import com.breckneck.deptbook.domain.util.LedgerDate
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
import java.io.File
import java.io.IOException
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
        CreateGoalsAction.ClearDate -> intent {
            reduce { state.copy(goal = state.goal.copy(goalDate = null), dateInvalid = false) }
        }
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
                isInitialized = true,
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
                isInitialized = true,
            )
        }
    }

    private fun onNameChanged(value: String) = intent {
        reduce { state.copy(goal = state.goal.copy(name = value.take(CreateGoalsFormValidation.MAX_NAME_LENGTH)), nameError = null) }
    }

    private fun onSumChanged(value: String) = intent {
        if (DecimalInput.isEditable(value, state.goal.currency)) {
            reduce { state.copy(goal = state.goal.copy(sum = value), sumError = null) }
        }
    }

    private fun onSavedSumChanged(value: String) = intent {
        if (DecimalInput.isEditable(value, state.goal.currency)) {
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
                dateInvalid = false,
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
                photoFailed = false,
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
                photoFailed = false,
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
        val originalInCurrency = originalGoal?.takeIf { it.currency == goal.currency }
        val (target, sumError) = CreateGoalsFormValidation.validateSum(sumText, goal.currency, originalInCurrency?.sum)
        val (saved, savedSumError) =
            CreateGoalsFormValidation.validateSavedSum(savedSumText, target, goal.currency, originalInCurrency?.savedSum)

        val deadline = goal.goalDate?.let(LedgerDate::fromDate)
        val originalDeadline = originalGoal?.presentationGoalDate?.let(LedgerDate::fromDate)
        val dateInvalid = deadline != null && deadline != originalDeadline && deadline <= LedgerDate.today()
        if (nameError != null || sumError != null || savedSumError != null || dateInvalid) {
            reduce {
                state.copy(
                    nameError = nameError,
                    sumError = sumError,
                    savedSumError = savedSumError,
                    dateInvalid = dateInvalid,
                )
            }
            return@intent
        }

        reduce {
            state.copy(nameError = null, sumError = null, savedSumError = null,
                isSaving = true, saveFailed = false, photoFailed = false, dateInvalid = false)
        }
        val isEdit = state.isEditMode
        try {
            val savedGoal = withContext(Dispatchers.IO) {
                val original = if (isEdit) checkNotNull(originalGoal) else null
                val newPhoto = goal.imageUri?.let(::savePhotoToInternalStorage)
                try {
                    val savingGoal = goal.toDomain(
                        sum = checkNotNull(target),
                        savedSum = saved ?: BigDecimal.ZERO,
                        photoPath = newPhoto?.absolutePath ?: goal.imagePath,
                        originalGoal = original,
                    )
                    if (isEdit) updateGoal.execute(savingGoal) else setGoal.execute(savingGoal)
                    savingGoal
                } catch (failure: Exception) {
                    newPhoto?.delete()
                    throw failure
                }
            }
            // Keep the save guard until this screen is removed, including rapid repeated taps.
            postSideEffect(CreateGoalsSideEffect.NavigateBack(
                editedGoal = savedGoal.takeIf { isEdit }, saved = true,
            ))
        } catch (cancelled: CancellationException) {
            reduce { state.copy(isSaving = false) }
            throw cancelled
        } catch (_: GoalPhotoException) {
            reduce { state.copy(isSaving = false, photoFailed = true) }
        } catch (_: Exception) {
            reduce { state.copy(isSaving = false, saveFailed = true) }
        }
    }

    private fun onBackClick() = intent {
        if (!state.isSaving) postSideEffect(CreateGoalsSideEffect.NavigateBack())
    }

    private fun savePhotoToInternalStorage(uri: Uri): File {
        var photo: File? = null
        try {
            val destination = File.createTempFile("goal-photo-", ".img", context.filesDir)
            photo = destination
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Missing photo")
            input.use { source -> destination.outputStream().use { source.copyTo(it) } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destination.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid image")
            return destination
        } catch (failure: Exception) {
            photo?.delete()
            throw GoalPhotoException(failure)
        }
    }

    private class GoalPhotoException(cause: Exception) : IOException(cause)

    private fun currencyDisplayNameFor(currency: String): String =
        //todo fix this - remove currencyNames. Store currency in ENUM.
        container.stateFlow.value.currencyNames.firstOrNull { it.substringAfterLast(" ") == currency } ?: currency

    private fun currencyIndexOf(currency: String): Int =
        //todo fix this - remove currencyNames. Store currency in ENUM.
        container.stateFlow.value.currencyNames.indexOfFirst { it.substringAfterLast(" ") == currency }
            .coerceAtLeast(0)

}
