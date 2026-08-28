package dev.raiseexception.odin.accounting.presentation.accountdetail

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest {

    private val accountFinder = mockk<AccountFinder>()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "test-account-id"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = AccountDetailViewModel(accountId, accountFinder, testDispatcher)

    @Test
    fun `given an existing account, when the screen loads, then uiState is Content with the account`() = runTest {
        val savings = AccountBuilder().id(accountId).build()
        coEvery { accountFinder.find(accountId) } returns Outcome.Success(savings)
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.Content
            assertEquals(savings.id, state.account.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given the account is not found, when the screen loads, then uiState is NotFound`() = runTest {
        coEvery { accountFinder.find(accountId) } returns Outcome.Failure(
            AccountLookupError.NotFound(
                internalMessage = "Not found",
                externalMessage = "Cuenta no encontrada"
            )
        )
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AccountDetailUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a storage failure, when the screen loads, then uiState is Error with a Spanish message`() = runTest {
        coEvery { accountFinder.find(accountId) } returns Outcome.Failure(
            AccountLookupError.StorageFailure(
                internalMessage = "Storage error",
                externalMessage = "Error al cargar la cuenta"
            )
        )
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.Error
            assertEquals("Error al cargar la cuenta", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
