package dev.raiseexception.odin.testutil

import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner

class RecordingTransactionRunner : TransactionRunner {

    private val completedOutcomes = mutableListOf<Outcome<*>>()

    val rolledBack: Boolean get() = this.completedOutcomes.lastOrNull() is Outcome.Failure

    val committed: Boolean get() = this.completedOutcomes.lastOrNull() is Outcome.Success

    override suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T> =
        block().also { this.completedOutcomes.add(it) }
}
