package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class AccountLookupError : DomainError {
    data class NotFound(
        override val internalMessage: String,
        override val externalMessage: String
    ) : AccountLookupError()

    data class StorageFailure(
        override val internalMessage: String,
        override val externalMessage: String
    ) : AccountLookupError()

    data class CryptoFailure(
        override val internalMessage: String,
        override val externalMessage: String
    ) : AccountLookupError()
}
