package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class AccountUpdateError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidInput(
        val nameError: String?,
        val balanceError: String?,
        val currencyError: String?,
        val typeError: String?,
        val descriptionError: String?
    ) : AccountUpdateError(
        internalMessage = "One or more account fields are invalid",
        externalMessage = "Revisa los datos de la cuenta"
    )

    class DuplicateName(
        internalMessage: String,
        externalMessage: String
    ) : AccountUpdateError(internalMessage, externalMessage)

    class StorageFailure(
        internalMessage: String,
        externalMessage: String
    ) : AccountUpdateError(internalMessage, externalMessage)
}
