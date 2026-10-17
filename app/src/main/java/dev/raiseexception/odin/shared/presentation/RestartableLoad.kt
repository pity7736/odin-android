package dev.raiseexception.odin.shared.presentation

import kotlinx.coroutines.Job

class RestartableLoad(private val launchLoad: () -> Job) {

    private var currentLoad: Job? = null

    fun start() {
        this.currentLoad?.cancel()
        this.currentLoad = this.launchLoad()
    }

    fun stop() {
        this.currentLoad?.cancel()
        this.currentLoad = null
    }
}
