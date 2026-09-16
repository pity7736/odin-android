package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class TransferCreationError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidInput(
        val amountError: String?,
        val dateError: String?,
        val sourceAccountError: String?,
        val destinationAccountError: String?
    ) : TransferCreationError(
        internalMessage = "One or more transfer fields are invalid",
        externalMessage = "Revisa los datos de la transferencia"
    )

    class TransferCategoryNotFound(
        internalMessage: String,
        externalMessage: String
    ) : TransferCreationError(internalMessage, externalMessage)

    class StorageFailure(
        internalMessage: String,
        externalMessage: String
    ) : TransferCreationError(internalMessage, externalMessage)
}
