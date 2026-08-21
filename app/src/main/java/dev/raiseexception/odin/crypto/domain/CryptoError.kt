package dev.raiseexception.odin.crypto.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class CryptoError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidPassword : CryptoError(
        internalMessage = "Password must not be empty",
        externalMessage = "La contraseña no puede estar vacía"
    )

    class InvalidSalt : CryptoError(
        internalMessage = "Salt must be exactly 16 bytes",
        externalMessage = "Ocurrió un error en la configuración de la cuenta"
    )

    class InvalidKeySize : CryptoError(
        internalMessage = "Key size is invalid",
        externalMessage = "Ocurrió un error en la protección de datos"
    )

    class DecryptionFailed : CryptoError(
        internalMessage = "Decryption failed: authentication tag mismatch",
        externalMessage = "No se pudieron verificar los datos"
    )

    class MalformedData : CryptoError(
        internalMessage = "Data is malformed or truncated",
        externalMessage = "Los datos están incompletos o no son válidos"
    )
}
