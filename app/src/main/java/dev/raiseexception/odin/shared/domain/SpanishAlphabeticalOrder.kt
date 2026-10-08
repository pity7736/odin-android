package dev.raiseexception.odin.shared.domain

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

object SpanishAlphabeticalOrder {

    private const val ENYE = "ñ"
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val SPANISH_COLLATOR: Collator = Collator.getInstance(Locale.forLanguageTag("es"))

    val COMPARATOR: Comparator<String> = Comparator { first, second ->
        SPANISH_COLLATOR.compare(normalize(first), normalize(second))
    }

    fun normalize(text: String): String {
        val lowercased = Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)
        return lowercased.split(ENYE).joinToString(ENYE) { this.withoutAccents(it) }
    }

    private fun withoutAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
}
