package dev.raiseexception.odin.shared.domain

sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class Failure(val error: DomainError) : Outcome<Nothing>()
}

interface DomainError {
    val internalMessage: String
    val externalMessage: String
}
