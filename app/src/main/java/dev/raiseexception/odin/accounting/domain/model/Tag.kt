package dev.raiseexception.odin.accounting.domain.model

import com.github.f4b6a3.uuid.UuidCreator
import dev.raiseexception.odin.accounting.domain.TagNameError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.SpanishAlphabeticalOrder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class Tag private constructor(
    val id: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Instant
) {

    companion object {
        const val MAX_NAME_LENGTH = 30
        val ALPHABETICAL_ORDER: Comparator<Tag> = Comparator { first, second ->
            SpanishAlphabeticalOrder.COMPARATOR.compare(first.normalizedName, second.normalizedName)
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

        fun normalize(text: String): String = SpanishAlphabeticalOrder.normalize(text)

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
