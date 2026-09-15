package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {

    fun findById(id: String): Flow<Outcome<TransactionDetail>>
}
