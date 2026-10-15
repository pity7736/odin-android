package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class IncomeUpdateError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidInput(
        val amountError: String?,
        val dateError: String?,
        val categoryError: String?,
        val descriptionError: String? = null
    ) : IncomeUpdateError(
        internalMessage = "One or more income fields are invalid",
        externalMessage = "Revisa los datos del ingreso"
    )

    class TransferNotEditable(
        internalMessage: String,
        externalMessage: String = "Las transferencias no se pueden editar."
    ) : IncomeUpdateError(internalMessage, externalMessage)

    class StorageFailure(
        internalMessage: String,
        externalMessage: String = "No se pudo editar el ingreso. Inténtalo de nuevo."
    ) : IncomeUpdateError(internalMessage, externalMessage)
}
