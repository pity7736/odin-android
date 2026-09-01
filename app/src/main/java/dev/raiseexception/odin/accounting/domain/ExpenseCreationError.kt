package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class ExpenseCreationError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidInput(
        val amountError: String?,
        val dateError: String?,
        val categoryError: String?,
        val descriptionError: String? = null
    ) : ExpenseCreationError(
        internalMessage = "One or more expense fields are invalid",
        externalMessage = "Revisa los datos del gasto"
    )

    class CategoryNotFound(
        internalMessage: String,
        externalMessage: String
    ) : ExpenseCreationError(internalMessage, externalMessage)

    class CategoryWrongType(
        internalMessage: String,
        externalMessage: String
    ) : ExpenseCreationError(internalMessage, externalMessage)

    class CryptoFailure(
        internalMessage: String,
        externalMessage: String
    ) : ExpenseCreationError(internalMessage, externalMessage)

    class StorageFailure(
        internalMessage: String,
        externalMessage: String
    ) : ExpenseCreationError(internalMessage, externalMessage)
}
