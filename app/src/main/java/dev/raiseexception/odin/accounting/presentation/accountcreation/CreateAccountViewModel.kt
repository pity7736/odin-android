package dev.raiseexception.odin.accounting.presentation.accountcreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CreateAccountViewModel(
    private val accountCreator: AccountCreator
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateAccountUiState>(CreateAccountUiState.Idle)
    val uiState: StateFlow<CreateAccountUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    fun create(
        rawName: String,
        rawBalance: String,
        currency: Currency?,
        type: AccountType?,
        rawDescription: String
    ) {
        this.viewModelScope.launch {
            mutableUiState.value = CreateAccountUiState.Loading
            val outcome = accountCreator.create(rawName, rawBalance, currency, type, rawDescription)
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(NavigationTarget.AccountsList)
                is Outcome.Failure -> mutableUiState.value = this@CreateAccountViewModel.mapError(outcome.error)
            }
        }
    }

    private fun mapError(error: DomainError): CreateAccountUiState = when (error) {
        is AccountCreationError.InvalidInput -> CreateAccountUiState.ValidationError(
            nameError = error.nameError,
            balanceError = error.balanceError,
            currencyError = error.currencyError,
            typeError = error.typeError,
            descriptionError = error.descriptionError
        )

        is AccountCreationError.DuplicateName -> CreateAccountUiState.ValidationError(
            nameError = error.externalMessage
        )

        is AccountCreationError.CryptoFailure -> CreateAccountUiState.Error(error.externalMessage)
        is AccountCreationError.StorageFailure -> CreateAccountUiState.Error(error.externalMessage)
        else -> CreateAccountUiState.Error(error.externalMessage)
    }
}
