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
import java.math.BigDecimal

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
            val parsedBalance = parseBalance(rawBalance)
            val uiValidationError = validateSelections(currency, type, rawBalance, parsedBalance)
            if (uiValidationError != null) {
                mutableUiState.value = uiValidationError
                return@launch
            }
            val outcome = accountCreator.create(rawName, parsedBalance!!, currency!!, type!!, rawDescription)
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(NavigationTarget.AccountsList)
                is Outcome.Failure -> mutableUiState.value = this@CreateAccountViewModel.mapError(outcome.error)
            }
        }
    }

    private fun validateSelections(
        currency: Currency?,
        type: AccountType?,
        rawBalance: String,
        parsedBalance: BigDecimal?
    ): CreateAccountUiState.ValidationError? {
        val balanceError = when {
            rawBalance.isBlank() -> "El saldo inicial es obligatorio."
            parsedBalance == null -> "El saldo inicial no es un número válido."
            else -> null
        }
        val currencyError = if (currency == null) "Debes seleccionar una moneda." else null
        val typeError = if (type == null) "Debes seleccionar un tipo de cuenta." else null
        if (balanceError == null && currencyError == null && typeError == null) {
            return null
        }
        return CreateAccountUiState.ValidationError(
            balanceError = balanceError,
            currencyError = currencyError,
            typeError = typeError
        )
    }

    private fun mapError(error: DomainError): CreateAccountUiState = when (error) {
        is AccountCreationError.InvalidInput -> CreateAccountUiState.ValidationError(
            nameError = error.nameError,
            balanceError = error.balanceError,
            descriptionError = error.descriptionError
        )
        is AccountCreationError.DuplicateName -> CreateAccountUiState.ValidationError(
            nameError = error.externalMessage
        )
        is AccountCreationError.CryptoFailure -> CreateAccountUiState.Error(error.externalMessage)
        is AccountCreationError.StorageFailure -> CreateAccountUiState.Error(error.externalMessage)
        else -> CreateAccountUiState.Error(error.externalMessage)
    }

    private fun parseBalance(rawBalance: String): BigDecimal? = try {
        BigDecimal(rawBalance.trim())
    } catch (@Suppress("SwallowedException") exception: NumberFormatException) {
        null
    }
}
