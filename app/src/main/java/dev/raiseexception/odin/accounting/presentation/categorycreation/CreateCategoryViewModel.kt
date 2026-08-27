package dev.raiseexception.odin.accounting.presentation.categorycreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CreateCategoryViewModel(
    private val categoryCreator: CategoryCreator
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateCategoryUiState>(CreateCategoryUiState.Idle)
    val uiState: StateFlow<CreateCategoryUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    fun create(
        rawName: String,
        type: CategoryType?,
        rawDescription: String,
        color: String?
    ) {
        if (this.mutableUiState.value is CreateCategoryUiState.Loading) return
        this.mutableUiState.value = CreateCategoryUiState.Loading
        this.viewModelScope.launch {
            val outcome = categoryCreator.create(rawName, type, rawDescription, color)
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(NavigationTarget.CategoriesList)
                is Outcome.Failure -> mutableUiState.value = mapError(outcome.error)
            }
        }
    }

    private fun mapError(error: DomainError): CreateCategoryUiState = when (error) {
        is CategoryCreationError.InvalidInput -> CreateCategoryUiState.ValidationError(
            nameError = error.nameError,
            typeError = error.typeError,
            descriptionError = error.descriptionError,
            colorError = error.colorError
        )

        is CategoryCreationError.DuplicateName -> CreateCategoryUiState.ValidationError(
            nameError = error.externalMessage
        )

        is CategoryCreationError.CryptoFailure -> CreateCategoryUiState.Error(error.externalMessage)
        is CategoryCreationError.StorageFailure -> CreateCategoryUiState.Error(error.externalMessage)
        else -> CreateCategoryUiState.Error(error.externalMessage)
    }
}
