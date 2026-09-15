package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.accounting.domain.repository.TransactionRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow

class TransactionFinder(private val transactionRepository: TransactionRepository) {

    fun find(id: String): Flow<Outcome<TransactionDetail>> =
        this.transactionRepository.findById(id)
}
