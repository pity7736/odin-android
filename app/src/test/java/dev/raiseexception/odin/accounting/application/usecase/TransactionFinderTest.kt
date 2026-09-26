package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.accounting.domain.repository.TransactionRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TransactionFinderTest {

    private val transactionRepository = mockk<TransactionRepository>()
    private val transactionFinder = TransactionFinder(transactionRepository)

    @Test
    fun `given an existing transaction, when finding it, then returns the transaction detail`() = runTest {
        val detail = TransactionDetail(
            transaction = Income.restore(
                id = "tx-123",
                accountId = "acc-1",
                amount = Money.of(BigDecimal("1000.00"), Currency.COP),
                date = LocalDate.parse("2026-09-14"),
                categoryId = "cat-1",
                description = "Salario",
                createdAt = Instant.parse("2026-09-14T10:00:00Z")
            ),
            categoryName = "Salario",
            accountName = "Ahorros",
            isTransfer = false
        )
        every { transactionRepository.findById("tx-123") } returns flowOf(Outcome.Success(detail))
        val result = transactionFinder.find("tx-123").first()
        assertTrue(result is Outcome.Success)
        assertEquals(detail, (result as Outcome.Success).value)
    }

    @Test
    fun `given a missing transaction, when finding it, then propagates not found`() = runTest {
        every { transactionRepository.findById("missing") } returns flowOf(
            Outcome.Failure(
                TransactionLookupError.NotFound(
                    internalMessage = "Transaction with id missing not found",
                    externalMessage = "Transacción no encontrada"
                )
            )
        )
        val result = transactionFinder.find("missing").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
    }

    @Test
    fun `given a storage failure, when finding it, then propagates the error`() = runTest {
        every { transactionRepository.findById("any") } returns flowOf(
            Outcome.Failure(StorageError(internalMessage = "Storage error"))
        )
        val result = transactionFinder.find("any").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is StorageError)
    }
}
