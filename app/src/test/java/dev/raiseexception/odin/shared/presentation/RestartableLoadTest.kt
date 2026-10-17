package dev.raiseexception.odin.shared.presentation

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartableLoadTest {

    private val launchedJobs = mutableListOf<Job>()
    private val load = RestartableLoad { Job().also { launchedJobs.add(it) } }

    @Test
    fun `given no load, when starting, then one load is launched`() {
        load.start()
        assertEquals(1, launchedJobs.size)
        assertTrue(launchedJobs.single().isActive)
    }

    @Test
    fun `given a running load, when starting again, then the previous load is cancelled and a new one launched`() {
        load.start()
        load.start()
        assertEquals(2, launchedJobs.size)
        assertTrue(launchedJobs.first().isCancelled)
        assertTrue(launchedJobs.last().isActive)
    }

    @Test
    fun `given a running load, when stopping, then the load is cancelled`() {
        load.start()
        load.stop()
        assertEquals(1, launchedJobs.size)
        assertTrue(launchedJobs.single().isCancelled)
    }

    @Test
    fun `given a stopped load, when starting, then a new load is launched`() {
        load.start()
        load.stop()
        load.start()
        assertEquals(2, launchedJobs.size)
        assertTrue(launchedJobs.first().isCancelled)
        assertTrue(launchedJobs.last().isActive)
    }
}
