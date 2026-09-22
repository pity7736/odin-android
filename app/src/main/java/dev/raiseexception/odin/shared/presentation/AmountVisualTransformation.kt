package dev.raiseexception.odin.shared.presentation

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class AmountVisualTransformation(
    private val decimalSeparator: Char = DEFAULT_DECIMAL_SEPARATOR,
    private val groupingSeparator: Char = DEFAULT_GROUPING_SEPARATOR,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val separatorIndex = original.indexOf(decimalSeparator)
        val integerLength = if (separatorIndex >= 0) separatorIndex else original.length
        val integerPart = original.substring(0, integerLength)
        val remainder = original.substring(integerLength)
        val formatted = groupInteger(integerPart) + remainder
        val totalGroups = groupsFor(integerLength)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clampedOffset = offset.coerceIn(0, original.length)
                if (clampedOffset > integerLength) {
                    return clampedOffset + totalGroups
                }
                val charsAfter = integerLength - clampedOffset
                val groupsAfter = (charsAfter - 1).coerceAtLeast(0) / GROUP_SIZE
                return clampedOffset + totalGroups - groupsAfter
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clampedOffset = offset.coerceIn(0, formatted.length)
                val groupingCount = formatted.take(clampedOffset).count { it == groupingSeparator }
                return (clampedOffset - groupingCount).coerceIn(0, original.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }

    private fun groupInteger(integerPart: String): String =
        if (integerPart.isEmpty()) {
            ""
        } else {
            integerPart.reversed().chunked(GROUP_SIZE).joinToString(groupingSeparator.toString()).reversed()
        }

    private fun groupsFor(integerLength: Int): Int =
        if (integerLength <= 0) 0 else (integerLength - 1) / GROUP_SIZE

    companion object {
        private const val GROUP_SIZE = 3
    }
}
