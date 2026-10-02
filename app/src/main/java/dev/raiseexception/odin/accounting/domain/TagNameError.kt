package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class TagNameError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class Blank : TagNameError(
        internalMessage = "Tag name is blank",
        externalMessage = ""
    )

    class TooLong : TagNameError(
        internalMessage = "Tag name exceeds 30 characters",
        externalMessage = "La etiqueta no puede superar 30 caracteres."
    )
}
