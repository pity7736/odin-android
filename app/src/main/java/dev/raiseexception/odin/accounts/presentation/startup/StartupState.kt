package dev.raiseexception.odin.accounts.presentation.startup

sealed interface StartupState {
    data object Deciding : StartupState
    data class Decided(val startRoute: String) : StartupState
}
