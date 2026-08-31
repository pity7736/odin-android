package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.shared.domain.TransactionRunner

class VaultTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
