package dev.raiseexception.odin.shared.presentation

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnexpectedFailureSignalTest {

    private val uiState = MutableStateFlow("guardando")
    private val signal = UnexpectedFailureSignal(uiState)

    @Test
    fun `given a state before saving, when restoring it, then the state returns and one event is emitted`() =
        runTest {
            signal.restore("formulario")
            assertEquals("formulario", uiState.value)
            signal.event.test {
                assertEquals(Unit, awaitItem())
                expectNoEvents()
            }
        }
}
