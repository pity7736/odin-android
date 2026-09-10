package dev.raiseexception.odin.shared.domain

class StorageError(
    override val internalMessage: String,
    override val externalMessage: String = "Error al acceder a los datos"
) : DomainError
