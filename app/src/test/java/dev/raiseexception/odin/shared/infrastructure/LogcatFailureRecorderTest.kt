package dev.raiseexception.odin.shared.infrastructure

import android.util.Log
import dev.raiseexception.odin.shared.domain.UnexpectedFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class LogcatFailureRecorderTest {

    private val recorder = LogcatFailureRecorder()

    @Before
    fun setUp() {
        ShadowLog.clear()
    }

    @Test
    fun `given a record, when recording, then one error entry contains operation, type, cause types and frames`() {
        val failure = UnexpectedFailure.from(
            "accounts-list.load",
            IllegalStateException("falla", IllegalArgumentException("causa"))
        )
        recorder.record(failure)
        val entries = ShadowLog.getLogsForTag("OdinFailure")
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(Log.ERROR, entry.type)
        assertTrue(entry.msg.contains("accounts-list.load"))
        assertTrue(entry.msg.contains("java.lang.IllegalStateException"))
        assertTrue(entry.msg.contains("java.lang.IllegalArgumentException"))
        assertTrue(entry.msg.contains(failure.stackFrames.first()))
    }
}
