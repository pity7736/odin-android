package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountListerTest {

    private val accountRepository = mockk<AccountRepository>()
    private val accountLister = AccountLister(accountRepository)

    @Test
    fun `given repository emits accounts, when list, then returns those accounts`() = runTest {
        val savings = AccountBuilder().id("aaa").name("Ahorros").build()
        val checking = AccountBuilder().id("bbb").name("Corriente").build()
        every { accountRepository.getAll() } returns flowOf(Outcome.Success(listOf(savings, checking)))

        val result = accountLister.list().first()

        assertTrue(result is Outcome.Success)
        assertEquals(2, (result as Outcome.Success).value.size)
    }

    @Test
    fun `given repository emits empty list, when list, then returns empty list`() = runTest {
        every { accountRepository.getAll() } returns flowOf(Outcome.Success(emptyList()))

        val result = accountLister.list().first()

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.isEmpty())
    }

    @Test
    fun `given criteria with incomes and expenses, when list, then passes criteria to repository`() = runTest {
        val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
        every { accountRepository.getAll(criteria) } returns flowOf(Outcome.Success(emptyList()))

        accountLister.list(criteria).first()

        verify { accountRepository.getAll(criteria) }
    }
}
