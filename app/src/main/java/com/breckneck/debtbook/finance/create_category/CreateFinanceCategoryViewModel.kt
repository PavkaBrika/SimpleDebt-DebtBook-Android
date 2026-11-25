package com.breckneck.debtbook.finance.create_category

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.breckneck.debtbook.core.navigation.FinanceCategoryForm
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import androidx.lifecycle.ViewModel
import com.breckneck.deptbook.domain.model.FinanceCategory
import com.breckneck.deptbook.domain.usecase.FinanceCategory.SetFinanceCategory
import com.breckneck.deptbook.domain.util.FinanceCategoryState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

data class CreateFinanceCategoryState(
    val categoryName: String,
    val selectedImage: Int?,
    val selectedColor: String?,
    val financeCategoryState: FinanceCategoryState,
    val isNameErrorVisible: Boolean,
    val isImageErrorVisible: Boolean,
    val isColorErrorVisible: Boolean,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    companion object {
        fun initial() = CreateFinanceCategoryState(
            categoryName = "",
            selectedImage = null,
            selectedColor = null,
            financeCategoryState = FinanceCategoryState.EXPENSE,
            isNameErrorVisible = false,
            isImageErrorVisible = false,
            isColorErrorVisible = false,
        )
    }
}

sealed class CreateFinanceCategorySideEffect {
    data class CategorySaved(val id: Int) : CreateFinanceCategorySideEffect()
}

@HiltViewModel
class CreateFinanceCategoryViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val setFinanceCategory: SetFinanceCategory
) : ViewModel(), ContainerHost<CreateFinanceCategoryState, CreateFinanceCategorySideEffect> {

    private val submitting = AtomicBoolean(false)

    override val container = container<CreateFinanceCategoryState, CreateFinanceCategorySideEffect>(
        CreateFinanceCategoryState.initial().copy(
            categoryName = saved["name"] ?: "", selectedImage = saved["image"], selectedColor = saved["color"],
            financeCategoryState = FinanceCategoryState.valueOf(saved.toRoute<FinanceCategoryForm>().categoryState),
        )
    )


    fun onNameChange(value: String) = intent {
        if (value.length <= 60) {
            saved["name"] = value
            reduce {
                state.copy(
                    categoryName = value,
                    isNameErrorVisible = if (value.trim().isNotEmpty()) false else state.isNameErrorVisible
                )
            }
        }
    }

    fun onImageSelected(image: Int) = intent {
        saved["image"] = image
        reduce {
            state.copy(
                selectedImage = image,
                isImageErrorVisible = false
            )
        }
    }

    fun onColorSelected(color: String) = intent {
        saved["color"] = color
        reduce {
            state.copy(
                selectedColor = color,
                isColorErrorVisible = false
            )
        }
    }

    fun onSaveClick() = intent {
        val nameInvalid = state.categoryName.trim().isEmpty()
        val imageInvalid = state.selectedImage == null
        val colorInvalid = state.selectedColor == null

        if (nameInvalid || imageInvalid || colorInvalid) {
            reduce {
                state.copy(
                    isNameErrorVisible = nameInvalid,
                    isImageErrorVisible = imageInvalid,
                    isColorErrorVisible = colorInvalid
                )
            }
            return@intent
        }

        if (!submitting.compareAndSet(false, true)) return@intent
        val draft = state
        reduce { state.copy(saving = true, saveFailed = false) }
        try {
            val id = withContext(Dispatchers.IO) {
                setFinanceCategory.execute(
                    financeCategory = FinanceCategory(
                        name = draft.categoryName.trim(),
                        color = draft.selectedColor!!,
                        state = draft.financeCategoryState,
                        image = draft.selectedImage!!
                    )
                )
            }
            postSideEffect(CreateFinanceCategorySideEffect.CategorySaved(id.toInt()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            submitting.set(false)
            reduce { state.copy(saving = false, saveFailed = true) }
        }
    }
}
