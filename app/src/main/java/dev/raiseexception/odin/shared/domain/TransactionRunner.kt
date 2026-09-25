package dev.raiseexception.odin.shared.domain

interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> Outcome<T>): Outcome<T>
}
