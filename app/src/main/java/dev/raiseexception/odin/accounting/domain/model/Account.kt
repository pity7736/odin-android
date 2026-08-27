package dev.raiseexception.odin.accounting.domain.model

import com.github.f4b6a3.uuid.UuidCreator
import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal

class Account private constructor(
    val id: String,
    val name: String,
    val initialBalance: Money,
    val type: AccountType,
    val description: String,
    val createdAt: Instant
) {

    val currency: Currency get() = this.initialBalance.currency

    companion object {
        private const val MAX_NAME_LENGTH = 200
        private const val MAX_DESCRIPTION_LENGTH = 500
        private const val MAX_DECIMAL_PLACES = 2

        @Suppress("LongParameterList")
        fun restore(
            id: String,
            name: String,
            initialBalance: Money,
            type: AccountType,
            description: String,
            createdAt: Instant
        ): Account = Account(
            id = id,
            name = name,
            initialBalance = initialBalance,
            type = type,
            description = description,
            createdAt = createdAt
        )

        @Suppress("LongParameterList")
        fun create(
            name: String,
            initialBalance: String,
            currency: Currency?,
            type: AccountType?,
            description: String,
            clock: Clock = Clock.System
        ): Outcome<Account> {
            val trimmedName = name.trim()
            val trimmedDescription = description.trim()
            val amount = parseAmount(initialBalance)
            val nameError = validateName(trimmedName)
            val balanceError = validateBalance(initialBalance, amount)
            val currencyError = if (currency == null) "La moneda es obligatoria." else null
            val typeError = if (type == null) "El tipo de cuenta es obligatorio." else null
            val descriptionError = validateDescription(trimmedDescription)
            if (anyError(nameError, balanceError, currencyError, typeError, descriptionError)) {
                return Outcome.Failure(
                    AccountCreationError.InvalidInput(
                        nameError = nameError,
                        balanceError = balanceError,
                        currencyError = currencyError,
                        typeError = typeError,
                        descriptionError = descriptionError
                    )
                )
            }
            return Outcome.Success(
                Account(
                    id = UuidCreator.getTimeOrderedEpoch().toString(),
                    name = trimmedName,
                    initialBalance = Money.of(amount!!, currency!!),
                    type = type!!,
                    description = trimmedDescription,
                    createdAt = clock.now()
                )
            )
        }

        private fun parseAmount(rawBalance: String): BigDecimal? = try {
            BigDecimal(rawBalance.trim())
        } catch (@Suppress("SwallowedException") exception: NumberFormatException) {
            null
        }

        private fun validateName(trimmedName: String): String? = when {
            trimmedName.isBlank() -> "El nombre es obligatorio."
            trimmedName.length > MAX_NAME_LENGTH -> "El nombre no puede superar los $MAX_NAME_LENGTH caracteres."
            else -> null
        }

        private fun validateBalance(rawBalance: String, amount: BigDecimal?): String? = when {
            rawBalance.isBlank() -> "El saldo inicial es obligatorio."
            amount == null -> "El saldo inicial no es un número válido."
            amount.signum() < 0 -> "El saldo inicial no puede ser negativo."
            amount.scale() > MAX_DECIMAL_PLACES ->
                "El saldo inicial admite máximo $MAX_DECIMAL_PLACES decimales."
            else -> null
        }

        private fun validateDescription(trimmedDescription: String): String? = when {
            trimmedDescription.length > MAX_DESCRIPTION_LENGTH ->
                "La descripción no puede superar los $MAX_DESCRIPTION_LENGTH caracteres."
            else -> null
        }

        private fun anyError(vararg errors: String?): Boolean = errors.any { it != null }
    }
}
