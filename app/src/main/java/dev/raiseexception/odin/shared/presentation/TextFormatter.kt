package dev.raiseexception.odin.shared.presentation

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

fun capitalizeFirst(text: String): String =
    text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

object ThousandSeparatorTransformation : VisualTransformation {

    private const val GROUP_SIZE = 3
    private const val THOUSAND_SEPARATOR = '.'
    private const val DECIMAL_SEPARATOR = ','

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val dotIndex = original.indexOf('.')
        val integerPart = if (dotIndex >= 0) original.substring(0, dotIndex) else original
        val decimalPart = if (dotIndex >= 0) original.substring(dotIndex + 1) else null

        val formattedInteger = if (integerPart.isNotEmpty()) {
            integerPart.reversed().chunked(GROUP_SIZE).joinToString("$THOUSAND_SEPARATOR").reversed()
        } else {
            ""
        }

        val formatted = if (decimalPart != null) {
            "$formattedInteger$DECIMAL_SEPARATOR$decimalPart"
        } else {
            formattedInteger
        }

        val integerDotsCount = formattedInteger.count { it == THOUSAND_SEPARATOR }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clampedOffset = offset.coerceIn(0, original.length)
                if (clampedOffset == 0) return 0

                if (dotIndex >= 0 && clampedOffset > dotIndex) {
                    val integerFormattedLength = formattedInteger.length
                    val positionAfterDot = clampedOffset - dotIndex
                    return integerFormattedLength + positionAfterDot
                }

                val charsAfter = integerPart.length - clampedOffset
                val dotsAfter = if (charsAfter > 0) (charsAfter - 1) / GROUP_SIZE else 0
                return clampedOffset + integerDotsCount - dotsAfter
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clampedOffset = offset.coerceIn(0, formatted.length)
                val integerFormattedLength = formattedInteger.length

                if (decimalPart != null && clampedOffset > integerFormattedLength) {
                    val positionAfterComma = clampedOffset - integerFormattedLength
                    return (dotIndex + positionAfterComma).coerceIn(0, original.length)
                }

                val dotsCount = formatted.take(clampedOffset).count { it == THOUSAND_SEPARATOR }
                return (clampedOffset - dotsCount).coerceIn(0, original.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
