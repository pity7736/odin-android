package dev.raiseexception.odin.accounts.presentation.startup

import app.cash.turbine.test
import dev.raiseexception.odin.crypto.domain.repository.SaltRepository
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.presentation.Routes
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {

    private val saltRepository = mockk<SaltRepository>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
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
        val viewModel = StartupViewModel(saltRepository, failureRecorder)

        viewModel.state.test {
            assertEquals(StartupState.Deciding, awaitItem())
            assertEquals(StartupState.Decided(Routes.LOGIN), awaitItem())
        }
    }

    @Test
    fun `given no salt, when startup checked, then routes to registration`() = runTest {
        coEvery { saltRepository.exists() } returns false
        val viewModel = StartupViewModel(saltRepository, failureRecorder)

        viewModel.state.test {
            assertEquals(StartupState.Deciding, awaitItem())
            assertEquals(StartupState.Decided(Routes.REGISTRATION), awaitItem())
        }
    }

    @Test
    fun `given deciding throws, when starting, then UnexpectedFailure is shown and recorded`() = runTest {
        coEvery { saltRepository.exists() } throws IllegalStateException("falla")
        val viewModel = StartupViewModel(saltRepository, failureRecorder)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(StartupState.UnexpectedFailure, viewModel.state.value)
        verify(exactly = 1) { failureRecorder.record(any()) }
    }

    @Test
    fun `given no result within 10 seconds, when starting, then UnexpectedFailure is shown`() = runTest {
        coEvery { saltRepository.exists() } coAnswers { awaitCancellation() }
        val viewModel = StartupViewModel(saltRepository, failureRecorder)
        testDispatcher.scheduler.advanceTimeBy(10.seconds)
        testDispatcher.scheduler.runCurrent()
        assertEquals(StartupState.UnexpectedFailure, viewModel.state.value)
        verify(exactly = 1) { failureRecorder.record(any()) }
    }

    @Test
    fun `given UnexpectedFailure, when starting again succeeds, then the start route is decided`() = runTest {
        coEvery { saltRepository.exists() } throws IllegalStateException("falla")
        val viewModel = StartupViewModel(saltRepository, failureRecorder)
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { saltRepository.exists() } returns true
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(StartupState.Decided(Routes.LOGIN), viewModel.state.value)
    }

    @Test
    fun `given UnexpectedFailure, when starting again fails, then UnexpectedFailure is shown and recorded twice`() =
        runTest {
            coEvery { saltRepository.exists() } throws IllegalStateException("falla")
            val viewModel = StartupViewModel(saltRepository, failureRecorder)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(StartupState.UnexpectedFailure, viewModel.state.value)
            verify(exactly = 2) { failureRecorder.record(any()) }
        }
}
