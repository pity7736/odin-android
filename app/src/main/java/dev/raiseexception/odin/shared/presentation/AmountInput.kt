package dev.raiseexception.odin.shared.presentation

const val DEFAULT_DECIMAL_SEPARATOR: Char = ','
const val DEFAULT_GROUPING_SEPARATOR: Char = '.'
const val DEFAULT_MAX_DECIMALS: Int = 2

fun filterAmountInput(
    text: String,
    decimalSeparator: Char = DEFAULT_DECIMAL_SEPARATOR,
    maxDecimals: Int = DEFAULT_MAX_DECIMALS,
): String {
    val accepted = StringBuilder()
    var decimalSeparatorSeen = false
    var decimalDigits = 0
    for (character in text) {
        when {
            character in '0'..'9' && !decimalSeparatorSeen -> accepted.append(character)
            character in '0'..'9' && decimalDigits < maxDecimals -> {
                accepted.append(character)
                decimalDigits++
            }
            character == decimalSeparator && !decimalSeparatorSeen -> {
                accepted.append(character)
                decimalSeparatorSeen = true
            }
        }
    }
    return accepted.toString()
}

fun amountInputToRaw(
    text: String,
    decimalSeparator: Char = DEFAULT_DECIMAL_SEPARATOR,
): String = text.replace(decimalSeparator, '.')
