package dev.raiseexception.odin.accounting.domain.model

import com.github.f4b6a3.uuid.UuidCreator
import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class Category private constructor(
    val id: String,
    val name: String,
    val type: CategoryType,
    val description: String,
    val color: String,
    val createdAt: Instant
) {

    companion object {
        val DEFAULT_PALETTE: List<String> = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
            "#2196F3", "#00BCD4", "#009688", "#4CAF50", "#8BC34A",
            "#CDDC39", "#FFC107", "#FF9800", "#FF5722", "#795548",
            "#9E9E9E", "#607D8B", "#FF80AB", "#B39DDB", "#80DEEA"
        )

        private const val MAX_NAME_LENGTH = 200
        private const val MAX_DESCRIPTION_LENGTH = 500
        private val COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

        @Suppress("LongParameterList")
        fun restore(
            id: String,
            name: String,
            type: CategoryType,
            description: String,
            color: String,
            createdAt: Instant
        ): Category = Category(
            id = id,
            name = name,
            type = type,
            description = description,
            color = color,
            createdAt = createdAt
        )

        fun create(
            name: String,
            type: CategoryType?,
            description: String,
            color: String,
            clock: Clock = Clock.System
        ): Outcome<Category> {
            val trimmedName = name.trim()
            val trimmedDescription = description.trim()
            val nameError = validateName(trimmedName)
            val typeError = if (type == null) "El tipo de categoría es obligatorio." else null
            val descriptionError = validateDescription(trimmedDescription)
            val colorError = validateColor(color)
            if (anyError(nameError, typeError, descriptionError, colorError)) {
                return Outcome.Failure(
                    CategoryCreationError.InvalidInput(
                        nameError = nameError,
                        typeError = typeError,
                        descriptionError = descriptionError,
                        colorError = colorError
                    )
                )
            }
            return Outcome.Success(
                Category(
                    id = UuidCreator.getTimeOrderedEpoch().toString(),
                    name = trimmedName,
                    type = type!!,
                    description = trimmedDescription,
                    color = color,
                    createdAt = clock.now()
                )
            )
        }

        private fun validateName(trimmedName: String): String? = when {
            trimmedName.isBlank() -> "El nombre es obligatorio."
            trimmedName.length > MAX_NAME_LENGTH ->
                "El nombre no puede superar los $MAX_NAME_LENGTH caracteres."
            else -> null
        }

        private fun validateDescription(trimmedDescription: String): String? = when {
            trimmedDescription.length > MAX_DESCRIPTION_LENGTH ->
                "La descripción no puede superar los $MAX_DESCRIPTION_LENGTH caracteres."
            else -> null
        }

        private fun validateColor(color: String): String? = when {
            !COLOR_REGEX.matches(color) -> "El color debe ser un valor hexadecimal válido (#RRGGBB)."
            else -> null
        }

        private fun anyError(vararg errors: String?): Boolean = errors.any { it != null }
    }
}
