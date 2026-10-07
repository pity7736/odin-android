package dev.raiseexception.odin.accounting.domain.model

enum class MoneyAccountKind {
    SAVINGS,
    CASH
}

fun MoneyAccountKind.toAccountType(): AccountType = when (this) {
    MoneyAccountKind.SAVINGS -> AccountType.SAVINGS
    MoneyAccountKind.CASH -> AccountType.CASH
}
