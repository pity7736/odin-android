package dev.raiseexception.odin.shared.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnexpectedFailureTest {

    @Test
    fun `given a failure whose message holds private text, when building the record, then no field contains it`() {
        val failure = IllegalStateException("Cuenta Ahorros Nómina saldo 1.250.000 clave S3cr3t-P4ss!")
        val record = UnexpectedFailure.from("expense-creation.submit", failure)
        val recordText = listOf(record.operation, record.failureType) + record.causeTypes + record.stackFrames
        assertFalse(recordText.any { it.contains("Ahorros Nómina") })
        assertFalse(recordText.any { it.contains("1.250.000") })
        assertFalse(recordText.any { it.contains("S3cr3t-P4ss!") })
    }

    @Test
    fun `given a cause chain, when building the record, then cause types are in order without messages`() {
        val rootCause = IllegalArgumentException("monto 500.000")
        val middleCause = RuntimeException("cuenta Efectivo", rootCause)
        val failure = IllegalStateException("clave S3cr3t", middleCause)
        val record = UnexpectedFailure.from("login.submit", failure)
        val recordText = listOf(record.operation, record.failureType) + record.causeTypes + record.stackFrames
        assertEquals(
            listOf("java.lang.RuntimeException", "java.lang.IllegalArgumentException"),
            record.causeTypes
        )
        assertFalse(recordText.any { it.contains("500.000") })
        assertFalse(recordText.any { it.contains("Efectivo") })
        assertFalse(recordText.any { it.contains("S3cr3t") })
    }

    @Test
    fun `given a failure, when building the record, then type, frames and operation are present`() {
        val failure = IllegalStateException("detalle")
        val record = UnexpectedFailure.from("accounts-list.load", failure)
        assertEquals("accounts-list.load", record.operation)
        assertEquals("java.lang.IllegalStateException", record.failureType)
        assertTrue(record.stackFrames.first().contains("UnexpectedFailureTest"))
    }

    @Test
    fun `given a failure whose causes loop back, when building the record, then each cause is listed once`() {
        val firstCause = RuntimeException("primera")
        val secondCause = IllegalArgumentException("segunda", firstCause)
        firstCause.initCause(secondCause)
        val failure = IllegalStateException("falla", firstCause)
        val record = UnexpectedFailure.from("home.load", failure)
        assertEquals(
            listOf("java.lang.RuntimeException", "java.lang.IllegalArgumentException"),
            record.causeTypes
        )
    }
}
