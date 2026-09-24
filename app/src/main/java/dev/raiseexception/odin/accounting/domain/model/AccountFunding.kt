package dev.raiseexception.odin.accounting.domain.model

sealed interface AccountFunding {
    val currency: Currency

    data class Funds(val initialBalance: Money) : AccountFunding {
        override val currency: Currency get() = this.initialBalance.currency
    }
}
