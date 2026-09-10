package dev.raiseexception.odin.shared.infrastructure.persistence

import androidx.room.withTransaction
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.TransactionRunner

class RoomTransactionRunner(
    private val database: OdinDatabase
) : TransactionRunner {

    override suspend fun <T> run(block: suspend () -> T): T =
        this.database.withTransaction { block() }
}
