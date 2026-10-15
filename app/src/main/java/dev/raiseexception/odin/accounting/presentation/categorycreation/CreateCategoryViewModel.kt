package dev.raiseexception.odin.accounting.presentation.categorycreation

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureSignal
import dev.raiseexception.odin.shared.presentation.launchOperation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class CreateCategoryViewModel(
    private val categoryCreator: CategoryCreator,
    private val failureRecorder: FailureRecorder,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateCategoryUiState>(CreateCategoryUiState.Idle)
    val uiState: StateFlow<CreateCategoryUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    fun create(
        rawName: String,
        type: CategoryType?,
        rawDescription: String,
        color: String?
    ) {
        val stateBeforeSaving = this.mutableUiState.value
        if (stateBeforeSaving is CreateCategoryUiState.Loading) return
        this.mutableUiState.value = CreateCategoryUiState.Loading
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = { this.categoryCreator.create(rawName, type, rawDescription, color) },
            onResult = { outcome -> this.handle(outcome) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(stateBeforeSaving) }
        )
    }

    private suspend fun handle(outcome: Outcome<Category>) {
        when (outcome) {
            is Outcome.Success -> this.navigationChannel.send(NavigationTarget.CategoriesList)
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(outcome.error)
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

    private companion object {
        const val SUBMIT_OPERATION = "category-creation.submit"
    }
}
