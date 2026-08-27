package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class CategoryCreationError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidInput(
        val nameError: String?,
        val typeError: String?,
        val descriptionError: String?,
        val colorError: String? = null
    ) : CategoryCreationError(
        internalMessage = "One or more category fields are invalid",
        externalMessage = "Revisa los datos de la categoría"
    )

    class DuplicateName(
        internalMessage: String,
        externalMessage: String
    ) : CategoryCreationError(internalMessage, externalMessage)

    class CryptoFailure(
        internalMessage: String,
        externalMessage: String
    ) : CategoryCreationError(internalMessage, externalMessage)

    class StorageFailure(
        internalMessage: String,
        externalMessage: String
    ) : CategoryCreationError(internalMessage, externalMessage)
}
