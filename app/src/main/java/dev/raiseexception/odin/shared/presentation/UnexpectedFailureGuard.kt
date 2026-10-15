package dev.raiseexception.odin.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.UnexpectedFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

private val UNEXPECTED_FAILURE_DEADLINE = 10.seconds

fun <T> ViewModel.launchOperation(
    operation: String,
    recorder: FailureRecorder,
    work: suspend () -> T,
    onResult: suspend (T) -> Unit,
    onUnexpectedFailure: () -> Unit,
): Job =
    this.viewModelScope.launch {
        guard(operation, recorder, onUnexpectedFailure) {
            onResult(work())
        }
    }

fun <T> ViewModel.launchObservation(
    operation: String,
    recorder: FailureRecorder,
    flow: Flow<T>,
    onEach: suspend (T) -> Unit,
    onUnexpectedFailure: () -> Unit,
): Job =
    this.viewModelScope.launch {
        guard(operation, recorder, onUnexpectedFailure) {
            val firstEmission = CompletableDeferred<Unit>()
            val collecting = startDetached {
                flow.collect { value ->
                    firstEmission.complete(Unit)
                    currentCoroutineContext().ensureActive()
                    onEach(value)
                }
            }
            collecting.invokeOnCompletion { firstEmission.complete(Unit) }
            awaitWithinDeadline(collecting) { firstEmission.await() }
            collecting.await()
        }
    }

private suspend fun guard(
    operation: String,
    recorder: FailureRecorder,
    onUnexpectedFailure: () -> Unit,
    block: suspend () -> Unit,
) {
    try {
        block()
    } catch (cancellation: CancellationException) {
        currentCoroutineContext().ensureActive()
        report(operation, recorder, cancellation, onUnexpectedFailure)
    } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
        report(operation, recorder, failure, onUnexpectedFailure)
    }
}

private fun <T> CoroutineScope.startDetached(work: suspend () -> T): Deferred<T> {
    val supervisor = SupervisorJob(this.coroutineContext.job)
    val running = (this + supervisor).async { work() }
    supervisor.complete()
    return running
}

private suspend fun <T> awaitWithinDeadline(running: Deferred<*>, waiting: suspend () -> T): T {
    try {
        return withTimeout(UNEXPECTED_FAILURE_DEADLINE) { waiting() }
    } catch (timeout: TimeoutCancellationException) {
        running.cancel()
        throw timeout
    }
}

private fun report(
    operation: String,
    recorder: FailureRecorder,
    failure: Throwable,
    onUnexpectedFailure: () -> Unit,
) {
    recorder.record(UnexpectedFailure.from(operation, failure))
    onUnexpectedFailure()
}
