package dev.raiseexception.odin.accounting.domain

import dev.raiseexception.odin.shared.domain.DomainError

sealed class TransactionDeletionError(
    override val internalMessage: String,
    override val externalMessage: String
) : DomainError {

    class HistoryBreach(
        externalMessage: String
    ) : TransactionDeletionError(
        internalMessage = "Deleting the transaction breaks the account history",
        externalMessage = externalMessage
    )

    class TransferNotDeletable(
        internalMessage: String,
        externalMessage: String = "Las transferencias no se pueden eliminar."
    ) : TransactionDeletionError(internalMessage, externalMessage)

    class GmfTaxNotDeletable(
        internalMessage: String,
        externalMessage: String = "El 4x1000 no se puede eliminar."
    ) : TransactionDeletionError(internalMessage, externalMessage)

    class StorageFailure(
        internalMessage: String,
        externalMessage: String
    ) : TransactionDeletionError(internalMessage, externalMessage)
}
