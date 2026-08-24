package dev.raiseexception.odin.accounts.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class LoginError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class EmptyPassword(
        internalMessage: String,
        externalMessage: String
    ) : LoginError(internalMessage, externalMessage)

    class InvalidCredentials(
        internalMessage: String,
        externalMessage: String
    ) : LoginError(internalMessage, externalMessage)

    class CryptoFailure(
        internalMessage: String,
        externalMessage: String
    ) : LoginError(internalMessage, externalMessage)

    class UserNotFound(
        internalMessage: String,
        externalMessage: String
    ) : LoginError(internalMessage, externalMessage)
}
