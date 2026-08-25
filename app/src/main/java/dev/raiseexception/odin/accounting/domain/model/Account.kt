package dev.raiseexception.odin.accounting.domain.model

import com.github.f4b6a3.uuid.UuidCreator
import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import java.math.BigDecimal

class Account private constructor(
    val id: String,
    val name: String,
    val initialBalance: Money,
    val type: AccountType,
    val description: String
) {

    val currency: Currency get() = this.initialBalance.currency

    companion object {
        private const val MAX_NAME_LENGTH = 200
        private const val MAX_DESCRIPTION_LENGTH = 500
        private const val MAX_DECIMAL_PLACES = 2

        fun create(
            name: String,
            initialBalance: BigDecimal,
            currency: Currency,
            type: AccountType,
            description: String
        ): Outcome<Account> {
            val trimmedName = name.trim()
            val trimmedDescription = description.trim()
            val nameError = validateName(trimmedName)
            val balanceError = validateBalance(initialBalance)
            val descriptionError = validateDescription(trimmedDescription)
            if (nameError != null || balanceError != null || descriptionError != null) {
                return Outcome.Failure(
                    AccountCreationError.InvalidInput(
                        nameError = nameError,
                        balanceError = balanceError,
                        descriptionError = descriptionError
                    )
                )
            }
            return Outcome.Success(
                Account(
                    id = UuidCreator.getTimeOrderedEpoch().toString(),
                    name = trimmedName,
                    initialBalance = Money.of(initialBalance, currency),
                    type = type,
                    description = trimmedDescription
                )
            )
        }

        private fun validateName(trimmedName: String): String? = when {
            trimmedName.isBlank() -> "El nombre es obligatorio."
            trimmedName.length > MAX_NAME_LENGTH -> "El nombre no puede superar los $MAX_NAME_LENGTH caracteres."
            else -> null
        }

        private fun validateBalance(initialBalance: BigDecimal): String? = when {
            initialBalance.signum() < 0 -> "El saldo inicial no puede ser negativo."
            initialBalance.scale() > MAX_DECIMAL_PLACES ->
                "El saldo inicial admite máximo $MAX_DECIMAL_PLACES decimales."
            else -> null
        }

        private fun validateDescription(trimmedDescription: String): String? = when {
            trimmedDescription.length > MAX_DESCRIPTION_LENGTH ->
                "La descripción no puede superar los $MAX_DESCRIPTION_LENGTH caracteres."
            else -> null
        }
    }
}
