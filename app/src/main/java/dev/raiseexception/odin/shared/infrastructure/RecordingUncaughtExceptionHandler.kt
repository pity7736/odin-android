package dev.raiseexception.odin.shared.infrastructure

import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.UnexpectedFailure

class RecordingUncaughtExceptionHandler(
    private val recorder: FailureRecorder,
    private val originalHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, failure: Throwable) {
        runCatching { this.recorder.record(UnexpectedFailure.from(OPERATION, failure)) }
        this.originalHandler?.uncaughtException(thread, failure)
    }

    private companion object {
        const val OPERATION = "app"
    }
}
