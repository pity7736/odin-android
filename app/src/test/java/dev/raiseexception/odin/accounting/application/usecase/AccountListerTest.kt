package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.every
import io.mockk.mockk
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
        every { accountRepository.getAll() } returns flowOf(listOf(savings, checking))

        val result = accountLister.list().first()

        assertEquals(2, result.size)
    }

    @Test
    fun `given repository emits empty list, when list, then returns empty list`() = runTest {
        every { accountRepository.getAll() } returns flowOf(emptyList())

        val result = accountLister.list().first()

        assertTrue(result.isEmpty())
    }
}
