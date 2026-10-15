package dev.raiseexception.odin.shared.infrastructure

import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.UnexpectedFailure
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecordingUncaughtExceptionHandlerTest {

    private val recorder: FailureRecorder = mockk(relaxed = true)
    private val originalHandler: Thread.UncaughtExceptionHandler = mockk(relaxed = true)
    private val handler = RecordingUncaughtExceptionHandler(recorder, originalHandler)

    @Test
    fun `given an uncaught failure, when handled, then it is recorded as app and passed to the original handler`() {
        val thread = Thread.currentThread()
        val failure = IllegalStateException("falla")
        val recorded = slot<UnexpectedFailure>()
        every { recorder.record(capture(recorded)) } returns Unit
        handler.uncaughtException(thread, failure)
        assertEquals("app", recorded.captured.operation)
        verifyOrder {
            recorder.record(any())
            originalHandler.uncaughtException(thread, failure)
        }
    }

    @Test
    fun `given the recorder throws, when handled, then the original handler is still called`() {
        val thread = Thread.currentThread()
        val failure = IllegalStateException("falla")
        every { recorder.record(any()) } throws IllegalStateException("recorder broken")
        handler.uncaughtException(thread, failure)
        verify(exactly = 1) { originalHandler.uncaughtException(thread, failure) }
    }

    @Test
    fun `given the failure message holds private text, when handled, then the record does not contain it`() {
        val failure = IllegalStateException("Cuenta Ahorros saldo 1.250.000 clave S3cr3t")
        val recorded = slot<UnexpectedFailure>()
        every { recorder.record(capture(recorded)) } returns Unit
        handler.uncaughtException(Thread.currentThread(), failure)
        val record = recorded.captured
        val recordText = listOf(record.operation, record.failureType) + record.causeTypes + record.stackFrames
        assertFalse(recordText.any { it.contains("Ahorros") })
        assertFalse(recordText.any { it.contains("1.250.000") })
        assertFalse(recordText.any { it.contains("S3cr3t") })
    }
}
