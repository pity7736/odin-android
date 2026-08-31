package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class IncomeCreationError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidInput(
        val amountError: String?,
        val dateError: String?,
        val categoryError: String?,
        val descriptionError: String? = null
    ) : IncomeCreationError(
        internalMessage = "One or more income fields are invalid",
        externalMessage = "Revisa los datos del ingreso"
    )

    class CategoryNotFound(
        internalMessage: String,
        externalMessage: String
    ) : IncomeCreationError(internalMessage, externalMessage)

    class CategoryWrongType(
        internalMessage: String,
        externalMessage: String
    ) : IncomeCreationError(internalMessage, externalMessage)

    class CryptoFailure(
        internalMessage: String,
        externalMessage: String
    ) : IncomeCreationError(internalMessage, externalMessage)

    class StorageFailure(
        internalMessage: String,
        externalMessage: String
    ) : IncomeCreationError(internalMessage, externalMessage)
}
