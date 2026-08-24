package dev.raiseexception.odin.accounts.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.shared.presentation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StartupViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val mutableState = MutableStateFlow<StartupState>(StartupState.Deciding)
    val state: StateFlow<StartupState> = this.mutableState.asStateFlow()

    init {
        this.decideStartRoute()
    }

    private fun decideStartRoute() {
        this.viewModelScope.launch {
            val startRoute = if (userRepository.exists()) Routes.LOGIN else Routes.REGISTRATION
            mutableState.value = StartupState.Decided(startRoute)
        }
    }
}
