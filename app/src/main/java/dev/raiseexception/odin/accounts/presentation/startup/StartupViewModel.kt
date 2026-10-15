package dev.raiseexception.odin.accounts.presentation.startup

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.crypto.domain.repository.SaltRepository
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.Routes
import dev.raiseexception.odin.shared.presentation.launchObservation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class StartupViewModel(
    private val saltRepository: SaltRepository,
    private val failureRecorder: FailureRecorder,
) : ViewModel() {

    private val mutableState = MutableStateFlow<StartupState>(StartupState.Deciding)
    val state: StateFlow<StartupState> = this.mutableState.asStateFlow()

    private val restartableLoad = RestartableLoad { this.decideStartRoute() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableState.value = StartupState.Deciding
        this.restartableLoad.start()
    }

    private fun decideStartRoute(): Job =
        this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = flow { emit(saltRepository.exists()) },
            onEach = { saltExists ->
                val startRoute = if (saltExists) Routes.LOGIN else Routes.REGISTRATION
                this.mutableState.value = StartupState.Decided(startRoute)
            },
            onUnexpectedFailure = { this.mutableState.value = StartupState.UnexpectedFailure }
        )

    private companion object {
        const val LOAD_OPERATION = "startup.load"
    }
}
