package dev.raiseexception.odin.accounts.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class RegistrationError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidPassword(
        internalMessage: String,
        externalMessage: String
    ) : RegistrationError(internalMessage, externalMessage)

    class PasswordsDoNotMatch(
        internalMessage: String,
        externalMessage: String
    ) : RegistrationError(internalMessage, externalMessage)

    class CryptoFailure(
        internalMessage: String,
        externalMessage: String
    ) : RegistrationError(internalMessage, externalMessage)

    class StorageFailure(
        internalMessage: String,
        externalMessage: String
    ) : RegistrationError(internalMessage, externalMessage)

    class AlreadyRegistered(
        internalMessage: String,
        externalMessage: String
    ) : RegistrationError(internalMessage, externalMessage)
}
