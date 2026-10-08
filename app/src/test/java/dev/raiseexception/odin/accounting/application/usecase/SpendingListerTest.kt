package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.ExpenseBuilder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingListerTest {

    private val expenseRepository = mockk<ExpenseRepository>()
    private val spendingLister = SpendingLister(expenseRepository)
    private val periodStart = LocalDate.parse("2026-10-01")
    private val periodEnd = LocalDate.parse("2026-10-07")

    @Test
    fun `given expenses in the period, when listing, then emits the repository expenses for that period`() = runTest {
        val marketExpense = ExpenseBuilder().id("exp-market").date("2026-10-03").build()
        every {
            expenseRepository.findSpendingBetween(periodStart, periodEnd)
        } returns flowOf(Outcome.Success(listOf(marketExpense)))
        val result = spendingLister.list(periodStart, periodEnd).first()
        assertEquals(Outcome.Success(listOf(marketExpense)), result)
    }

    @Test
    fun `given a storage failure, when listing, then propagates the failure`() = runTest {
        val failure = Outcome.Failure(StorageError("disk error"))
        every { expenseRepository.findSpendingBetween(any(), any()) } returns flowOf(failure)
        val result = spendingLister.list(periodStart, periodEnd).first()
        assertEquals(failure, result)
    }
}
