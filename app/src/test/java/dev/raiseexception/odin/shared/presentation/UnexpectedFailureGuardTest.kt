package dev.raiseexception.odin.shared.presentation

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UnexpectedFailureGuardTest {

    private val testDispatcher = StandardTestDispatcher()
    private val recorder = mockk<FailureRecorder>(relaxed = true)
    private val viewModel = GuardedViewModel()
    private val results = mutableListOf<Any>()
    private val failureTimes = mutableListOf<Long>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given work that succeeds in time, when launching, then the result is delivered and nothing recorded`() =
        runTest {
            launchOperationOf { "listo" }
            advanceTimeBy(1.minutes)
            assertEquals(listOf("listo"), results)
            assertTrue(failureTimes.isEmpty())
            verify(exactly = 0) { recorder.record(any()) }
        }

    @Test
    fun `given an expected failure, when launching, then it is delivered untouched and nothing recorded`() =
        runTest {
            val expectedFailure = Outcome.Failure(SampleError)
            launchOperationOf { expectedFailure }
            advanceTimeBy(1.minutes)
            assertEquals(listOf(expectedFailure), results)
            assertTrue(failureTimes.isEmpty())
            verify(exactly = 0) { recorder.record(any()) }
        }

    @Test
    fun `given work that throws, when launching an operation, then it is recorded once and reported`() = runTest {
        launchOperationOf { throw IllegalStateException("falla") }
        advanceTimeBy(1.minutes)
        assertTrue(results.isEmpty())
        assertEquals(1, failureTimes.size)
        verify(exactly = 1) {
            recorder.record(match { it.operation == OPERATION && it.failureType == "java.lang.IllegalStateException" })
        }
    }

    @Test
    fun `given work that throws an error, when launching an operation, then it is recorded once and reported`() =
        runTest {
            launchOperationOf { throw UnsatisfiedLinkError("sqlcipher") }
            advanceTimeBy(1.minutes)
            assertTrue(results.isEmpty())
            assertEquals(1, failureTimes.size)
            verify(exactly = 1) {
                recorder.record(match { it.failureType == "java.lang.UnsatisfiedLinkError" })
            }
        }

    @Test
    fun `given work that takes 60 seconds, when launching, then the result is delivered and nothing is recorded`() =
        runTest {
            launchOperationOf {
                delay(60.seconds)
                "listo"
            }
            advanceTimeBy(2.minutes)
            assertEquals(listOf("listo"), results)
            assertTrue(failureTimes.isEmpty())
            verify(exactly = 0) { recorder.record(any()) }
        }

    @Test
    fun `given work that throws a cancellation while the guard is active, when launching, then it is reported`() =
        runTest {
            launchOperationOf { throw CancellationException("falla") }
            advanceTimeBy(1.minutes)
            assertTrue(results.isEmpty())
            assertEquals(1, failureTimes.size)
            verify(exactly = 1) { recorder.record(any()) }
        }

    @Test
    fun `given an operation cancelled from outside, when it is cancelled, then nothing is recorded or reported`() =
        runTest {
            val job = launchOperationOf {
                delay(5.seconds)
                "listo"
            }
            advanceTimeBy(2.seconds)
            job.cancel()
            advanceTimeBy(1.minutes)
            assertTrue(results.isEmpty())
            assertTrue(failureTimes.isEmpty())
            verify(exactly = 0) { recorder.record(any()) }
        }

    @Test
    fun `given a first emission in time and then silence for minutes, when observing, then nothing is reported`() =
        runTest {
            launchObservationOf(
                flow {
                    emit("primero")
                    delay(30.minutes)
                }
            )
            advanceTimeBy(20.minutes)
            assertEquals(listOf("primero"), results)
            assertTrue(failureTimes.isEmpty())
            verify(exactly = 0) { recorder.record(any()) }
        }

    @Test
    fun `given a first emission after 10 seconds, when observing, then it is recorded and reported at 10 seconds`() =
        runTest {
            launchObservationOf(
                flow {
                    delay(11.seconds)
                    emit("tarde")
                }
            )
            advanceTimeBy(1.minutes)
            assertEquals(listOf(DEADLINE_MILLISECONDS), failureTimes)
            verify(exactly = 1) { recorder.record(match { it.operation == OPERATION }) }
        }

    @Test
    fun `given an uncooperative upstream, when observing, then it is reported at 10 seconds`() = runTest {
        launchObservationOf(
            flow {
                withContext(NonCancellable) { delay(60.seconds) }
                emit("tarde")
            }
        )
        advanceTimeBy(2.minutes)
        assertEquals(listOf(DEADLINE_MILLISECONDS), failureTimes)
        verify(exactly = 1) { recorder.record(any()) }
    }

    @Test
    fun `given an upstream that throws after the first emission, when observing, then it is recorded and reported`() =
        runTest {
            launchObservationOf(
                flow {
                    emit("primero")
                    delay(1.minutes)
                    error("falla")
                }
            )
            advanceTimeBy(5.minutes)
            assertEquals(listOf("primero"), results)
            assertEquals(1, failureTimes.size)
            verify(exactly = 1) {
                recorder.record(match { it.failureType == "java.lang.IllegalStateException" })
            }
        }

    @Test
    fun `given emissions after a timeout, when observing, then they are not delivered`() = runTest {
        launchObservationOf(
            flow {
                withContext(NonCancellable) { delay(60.seconds) }
                emit("tarde")
                emit("más tarde")
            }
        )
        advanceTimeBy(2.minutes)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `given an upstream cancellation while the guard is active, when observing, then it is reported`() =
        runTest {
            launchObservationOf(
                flow {
                    emit("primero")
                    throw CancellationException("falla")
                }
            )
            advanceTimeBy(1.minutes)
            assertEquals(1, failureTimes.size)
            verify(exactly = 1) { recorder.record(any()) }
        }

    @Test
    fun `given an observation cancelled from outside, when it is cancelled, then nothing is recorded or reported`() =
        runTest {
            val job = launchObservationOf(
                flow {
                    emit("primero")
                    delay(1.minutes)
                    emit("segundo")
                }
            )
            runCurrent()
            job.cancel()
            advanceTimeBy(5.minutes)
            assertEquals(listOf("primero"), results)
            assertTrue(failureTimes.isEmpty())
            verify(exactly = 0) { recorder.record(any()) }
        }

    private fun TestScope.launchOperationOf(work: suspend () -> Any) =
        viewModel.launchOperation(
            operation = OPERATION,
            recorder = recorder,
            work = work,
            onResult = { result -> results.add(result) },
            onUnexpectedFailure = { failureTimes.add(testScheduler.currentTime) }
        )

    private fun TestScope.launchObservationOf(upstream: Flow<String>) =
        viewModel.launchObservation(
            operation = OPERATION,
            recorder = recorder,
            flow = upstream,
            onEach = { value -> results.add(value) },
            onUnexpectedFailure = { failureTimes.add(testScheduler.currentTime) }
        )

    private class GuardedViewModel : ViewModel()

    private object SampleError : DomainError {
        override val internalMessage = "Expected failure"
        override val externalMessage = "Falla esperada"
    }

    private companion object {
        const val OPERATION = "test.submit"
        val DEADLINE_MILLISECONDS = 10.seconds.inWholeMilliseconds
    }
}
