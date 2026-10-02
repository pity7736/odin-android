package dev.raiseexception.odin.accounting.domain.model

import com.github.f4b6a3.uuid.UuidCreator
import dev.raiseexception.odin.accounting.domain.TagNameError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

class Tag private constructor(
    val id: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Instant
) {

    companion object {
        const val MAX_NAME_LENGTH = 30
        private const val ENYE = "ñ"
        private val COMBINING_MARKS = Regex("\\p{Mn}+")
        private val SPANISH_COLLATOR: Collator = Collator.getInstance(Locale.forLanguageTag("es"))
        val ALPHABETICAL_ORDER: Comparator<Tag> = Comparator { first, second ->
            SPANISH_COLLATOR.compare(first.normalizedName, second.normalizedName)
        }

        fun create(name: String, clock: Clock = Clock.System): Outcome<Tag> =
            when (val validation = validateName(name)) {
                is Outcome.Failure -> validation
                is Outcome.Success -> Outcome.Success(
                    Tag(
                        id = UuidCreator.getTimeOrderedEpoch().toString(),
                        name = validation.value,
                        normalizedName = normalize(validation.value),
                        createdAt = clock.now()
                    )
                )
            }

        fun restore(id: String, name: String, normalizedName: String, createdAt: Instant): Tag =
            Tag(id = id, name = name, normalizedName = normalizedName, createdAt = createdAt)

        fun normalize(text: String): String {
            val lowercased = Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)
            return lowercased.split(ENYE).joinToString(ENYE) { withoutAccents(it) }
        }

        private fun withoutAccents(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

        fun validateName(name: String): Outcome<String> {
            val trimmedName = name.trim()
            return when {
                trimmedName.isEmpty() -> Outcome.Failure(TagNameError.Blank())
                trimmedName.length > MAX_NAME_LENGTH -> Outcome.Failure(TagNameError.TooLong())
                else -> Outcome.Success(trimmedName)
            }
        }
    }
}
