package dev.raiseexception.odin.shared.domain

interface FailureRecorder {
    fun record(failure: UnexpectedFailure)
}
