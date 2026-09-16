package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.Transfer
import dev.raiseexception.odin.shared.domain.Outcome

interface TransferRepository {
    suspend fun add(transfer: Transfer): Outcome<Unit>
}
