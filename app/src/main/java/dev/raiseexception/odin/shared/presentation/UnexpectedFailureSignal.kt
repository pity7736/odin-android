package dev.raiseexception.odin.shared.presentation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class UnexpectedFailureSignal<S>(private val uiState: MutableStateFlow<S>) {

    private val channel = Channel<Unit>(Channel.BUFFERED)
    val event: Flow<Unit> = this.channel.receiveAsFlow()

    fun restore(stateBeforeSaving: S) {
        this.uiState.value = stateBeforeSaving
        this.channel.trySend(Unit)
    }
}
