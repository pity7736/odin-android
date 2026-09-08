package dev.raiseexception.odin.accounts.domain.model

import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.crypto.domain.SensitivePassword
import dev.raiseexception.odin.shared.domain.Outcome

private const val MIN_LENGTH = 12
private const val MAX_LENGTH = 100

fun validatePassword(password: SensitivePassword): Outcome<Unit> {
    if (password.length < MIN_LENGTH) {
        return Outcome.Failure(
            RegistrationError.InvalidPassword(
                internalMessage = "Password must be at least $MIN_LENGTH characters",
                externalMessage = "La contraseña debe tener al menos $MIN_LENGTH caracteres"
            )
        )
    }
    if (password.length > MAX_LENGTH) {
        return Outcome.Failure(
            RegistrationError.InvalidPassword(
                internalMessage = "Password must be at most $MAX_LENGTH characters",
                externalMessage = "La contraseña debe tener como máximo $MAX_LENGTH caracteres"
            )
        )
    }
    return Outcome.Success(Unit)
}
