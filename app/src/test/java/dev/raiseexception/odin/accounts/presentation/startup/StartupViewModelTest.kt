package dev.raiseexception.odin.accounts.presentation.startup

import app.cash.turbine.test
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
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

    private val userRepository = mockk<UserRepository>()
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
    fun `given a registered user exists, when the app starts, then decides the login route`() = runTest {
        coEvery { userRepository.exists() } returns true
        val viewModel = StartupViewModel(userRepository)

        viewModel.state.test {
            assertEquals(StartupState.Deciding, awaitItem())
            assertEquals(StartupState.Decided(Routes.LOGIN), awaitItem())
        }
    }

    @Test
    fun `given no registered user, when the app starts, then decides the registration route`() = runTest {
        coEvery { userRepository.exists() } returns false
        val viewModel = StartupViewModel(userRepository)

        viewModel.state.test {
            assertEquals(StartupState.Deciding, awaitItem())
            assertEquals(StartupState.Decided(Routes.REGISTRATION), awaitItem())
        }
    }
}
