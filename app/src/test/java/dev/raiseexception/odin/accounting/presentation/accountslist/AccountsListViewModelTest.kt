package dev.raiseexception.odin.accounting.presentation.accountslist

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsListViewModelTest {

    private val accountLister = mockk<AccountLister>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = AccountsListViewModel(accountLister, testDispatcher)

    private fun account(id: String, name: String) = AccountBuilder().id(id).name(name).build()

    @Test
    fun `given lister emits empty list, when initialized, then ui state is Empty`() = runTest {
        every { accountLister.list() } returns flowOf(emptyList())
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AccountsListUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given lister emits accounts, when initialized, then ui state is Content with accounts`() = runTest {
        val savings = account("aaa", "Ahorros")
        val checking = account("bbb", "Corriente")
        every { accountLister.list() } returns flowOf(listOf(savings, checking))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as AccountsListUiState.Content
            assertEquals(2, content.accounts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given Content state, when an account is selected, then navigation event is AccountDetail`() = runTest {
        val savings = account("aaa", "Ahorros")
        every { accountLister.list() } returns flowOf(listOf(savings))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            viewModel.onAccountSelected("aaa")
            val event = awaitItem()
            assertTrue(event is AccountsListNavigationTarget.AccountDetail)
            assertEquals("aaa", (event as AccountsListNavigationTarget.AccountDetail).accountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `given lister throws, when initialized, then ui state is Error`() = runTest {
        every { accountLister.list() } returns flow { throw RuntimeException("Storage error") }
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountsListUiState.Error
            assertEquals("Error al cargar las cuentas", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
