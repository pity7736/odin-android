package dev.raiseexception.odin.accounts.presentation.startup

import app.cash.turbine.test
import dev.raiseexception.odin.crypto.domain.repository.SaltRepository
import dev.raiseexception.odin.shared.presentation.Routes
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
class StartupViewModelTest {

    private val saltRepository = mockk<SaltRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given salt exists, when startup checked, then routes to login`() = runTest {
        coEvery { saltRepository.exists() } returns true
        val viewModel = StartupViewModel(saltRepository)

        viewModel.state.test {
            assertEquals(StartupState.Deciding, awaitItem())
            assertEquals(StartupState.Decided(Routes.LOGIN), awaitItem())
        }
    }

    @Test
    fun `given no salt, when startup checked, then routes to registration`() = runTest {
        coEvery { saltRepository.exists() } returns false
        val viewModel = StartupViewModel(saltRepository)

        viewModel.state.test {
            assertEquals(StartupState.Deciding, awaitItem())
            assertEquals(StartupState.Decided(Routes.REGISTRATION), awaitItem())
        }
    }
}
