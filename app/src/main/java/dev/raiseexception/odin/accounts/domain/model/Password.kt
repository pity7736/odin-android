package dev.raiseexception.odin.accounts.domain.model

import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.shared.domain.Outcome

class Password private constructor(val value: String) {

    companion object {
        private const val MIN_LENGTH = 12
        private const val MAX_LENGTH = 100

        fun create(raw: String): Outcome<Password> {
            if (raw.length < MIN_LENGTH) {
                return Outcome.Failure(
                    RegistrationError.InvalidPassword(
                        internalMessage = "Password must be at least $MIN_LENGTH characters",
                        externalMessage = "La contraseña debe tener al menos $MIN_LENGTH caracteres"
                    )
                )
            }
            if (raw.length > MAX_LENGTH) {
                return Outcome.Failure(
                    RegistrationError.InvalidPassword(
                        internalMessage = "Password must be at most $MAX_LENGTH characters",
                        externalMessage = "La contraseña debe tener como máximo $MAX_LENGTH caracteres"
                    )
                )
            }
            return Outcome.Success(Password(raw))
        }
    }
}
