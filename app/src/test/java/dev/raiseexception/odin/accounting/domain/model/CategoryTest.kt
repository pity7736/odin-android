package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MAX_NAME_LENGTH = 200
private const val MAX_DESCRIPTION_LENGTH = 500

class CategoryTest {

    @Test
    fun `given a valid name and type, when creating, then returns the category`() {
        val result = Category.create(
            name = "  Alimentación  ",
            type = CategoryType.EXPENSE,
            description = "",
            color = "#E57373"
        )

        assertTrue(result is Outcome.Success)
        val category = (result as Outcome.Success).value
        assertEquals("Alimentación", category.name)
        assertEquals(CategoryType.EXPENSE, category.type)
        assertEquals("#E57373", category.color)
        assertTrue(category.id.isNotEmpty())
        assertNotNull(category.createdAt)
    }

    @Test
    fun `given an empty name, when creating, then returns InvalidInput with nameError set`() {
        val result = Category.create(
            name = "   ",
            type = CategoryType.EXPENSE,
            description = "",
            color = "#E57373"
        )

        assertEquals("El nombre es obligatorio.", failureInvalidInput(result).nameError)
    }

    @Test
    fun `given a name over 200 characters, when creating, then returns InvalidInput with nameError set`() {
        val result = Category.create(
            name = "a".repeat(MAX_NAME_LENGTH + 1),
            type = CategoryType.EXPENSE,
            description = "",
            color = "#E57373"
        )

        assertEquals(
            "El nombre no puede superar los $MAX_NAME_LENGTH caracteres.",
            failureInvalidInput(result).nameError
        )
    }

    @Test
    fun `given a null type, when creating, then returns InvalidInput with typeError set`() {
        val result = Category.create(
            name = "Alimentación",
            type = null,
            description = "",
            color = "#E57373"
        )

        assertEquals("El tipo de categoría es obligatorio.", failureInvalidInput(result).typeError)
    }

    @Test
    fun `given a description over 500 characters, when creating, then returns InvalidInput with descriptionError`() {
        val result = Category.create(
            name = "Alimentación",
            type = CategoryType.EXPENSE,
            description = "a".repeat(MAX_DESCRIPTION_LENGTH + 1),
            color = "#E57373"
        )

        assertEquals(
            "La descripción no puede superar los $MAX_DESCRIPTION_LENGTH caracteres.",
            failureInvalidInput(result).descriptionError
        )
    }

    @Test
    fun `given an invalid hex color, when creating, then returns InvalidInput with colorError set`() {
        val result = Category.create(
            name = "Alimentación",
            type = CategoryType.EXPENSE,
            description = "",
            color = "not-a-color"
        )

        assertNotNull(failureInvalidInput(result).colorError)
    }

    @Test
    fun `given valid input, when restoring, then all fields round-trip exactly`() {
        val knownInstant = Instant.parse("2026-01-01T00:00:00Z")

        val category = Category.restore(
            id = "test-id",
            name = "Salario",
            type = CategoryType.INCOME,
            description = "Ingresos mensuales",
            color = "#81C784",
            createdAt = knownInstant
        )

        assertEquals("test-id", category.id)
        assertEquals("Salario", category.name)
        assertEquals(CategoryType.INCOME, category.type)
        assertEquals("Ingresos mensuales", category.description)
        assertEquals("#81C784", category.color)
        assertEquals(knownInstant, category.createdAt)
    }

    private fun failureInvalidInput(result: Outcome<Category>): CategoryCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is CategoryCreationError.InvalidInput)
        return error as CategoryCreationError.InvalidInput
    }
}
