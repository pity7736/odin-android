package dev.raiseexception.odin.shared.infrastructure.persistence

import androidx.room.withTransaction
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner

class RoomTransactionRunner(
    private val database: OdinDatabase
) : TransactionRunner {

    override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> =
        try {
            this.database.withTransaction { this.rollbackOnFailure(block()) }
        } catch (rollback: FailedTransactionRollback) {
            rollback.failure
        }

    private fun <T> rollbackOnFailure(outcome: Outcome<T>): Outcome<T> =
        when (outcome) {
            is Outcome.Success -> outcome
            is Outcome.Failure -> throw FailedTransactionRollback(outcome)
        }
}

private class FailedTransactionRollback(
    val failure: Outcome.Failure
) : RuntimeException(failure.error.internalMessage)
