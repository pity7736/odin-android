package dev.raiseexception.odin.shared.presentation

import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money

private const val DECIMAL_PLACES = 2
private const val GROUP_SIZE = 3

fun formatMoney(money: Money): String {
    val symbol = currencySymbol(money.currency)
    val parts = money.amount.toPlainString().split(".")
    val integerPart = parts[0].reversed().chunked(GROUP_SIZE).joinToString(".").reversed()
    val decimalPart = if (parts.size > 1) parts[1].padEnd(DECIMAL_PLACES, '0') else "00"
    return "$symbol$integerPart,$decimalPart"
}

private fun currencySymbol(currency: Currency): String = when (currency) {
    Currency.COP -> "$"
    Currency.USD -> "US$"
    Currency.EUR -> "€"
}
