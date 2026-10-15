package dev.raiseexception.odin.shared.infrastructure

import android.util.Log
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.UnexpectedFailure

class LogcatFailureRecorder : FailureRecorder {

    override fun record(failure: UnexpectedFailure) {
        Log.e(TAG, this.entryOf(failure))
    }

    private fun entryOf(failure: UnexpectedFailure): String =
        listOf(
            "operation=${failure.operation}",
            "type=${failure.failureType}",
            "causes=${failure.causeTypes.joinToString(prefix = "[", postfix = "]")}",
        ).joinToString(separator = " ") + failure.stackFrames.joinToString(separator = "") { "\n\tat $it" }

    private companion object {
        const val TAG = "OdinFailure"
    }
}
