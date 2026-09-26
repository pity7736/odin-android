package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class ExpenseUpdateError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class InvalidInput(
        val amountError: String?,
        val dateError: String?,
        val categoryError: String?,
        val descriptionError: String? = null
    ) : ExpenseUpdateError(
        internalMessage = "One or more expense fields are invalid",
        externalMessage = "Revisa los datos del gasto"
    )

    class TransferNotEditable(
        internalMessage: String,
        externalMessage: String = "Las transferencias no se pueden editar."
    ) : ExpenseUpdateError(internalMessage, externalMessage)

    class StorageFailure(
        internalMessage: String,
        externalMessage: String = "No se pudo editar el gasto. Inténtalo de nuevo."
    ) : ExpenseUpdateError(internalMessage, externalMessage)
}
