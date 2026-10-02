package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class TagResolutionError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidName(val nameError: TagNameError) : TagResolutionError(
        internalMessage = nameError.internalMessage,
        externalMessage = nameError.externalMessage
    )

    class StorageFailure(
        internalMessage: String,
        externalMessage: String = "Error al acceder a los datos"
    ) : TagResolutionError(internalMessage, externalMessage)
}
