package dev.raiseexception.odin.shared.presentation

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

fun capitalizeFirst(text: String): String =
    text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

object ThousandSeparatorTransformation : VisualTransformation {

    private const val GROUP_SIZE = 3

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val formatted = original.reversed().chunked(GROUP_SIZE).joinToString(".").reversed()
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clampedOffset = offset.coerceIn(0, original.length)
                if (clampedOffset == 0) {
                    return 0
                }
                val charsAfter = original.length - clampedOffset
                val totalDots = (original.length - 1) / GROUP_SIZE
                val dotsAfter = (charsAfter - 1).coerceAtLeast(0) / GROUP_SIZE
                return clampedOffset + totalDots - dotsAfter
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clampedOffset = offset.coerceIn(0, formatted.length)
                val dotsCount = formatted.take(clampedOffset).count { it == '.' }
                return (clampedOffset - dotsCount).coerceIn(0, original.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
