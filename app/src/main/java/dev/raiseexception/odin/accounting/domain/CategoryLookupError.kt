package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class CategoryLookupError : DomainError {
    data class NotFound(
        override val internalMessage: String,
        override val externalMessage: String
    ) : CategoryLookupError()
}
