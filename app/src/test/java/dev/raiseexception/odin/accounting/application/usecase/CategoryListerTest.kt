package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.CategoryBuilder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryListerTest {

    private val categoryRepository = mockk<CategoryRepository>()
    private val categoryLister = CategoryLister(categoryRepository)

    @Test
    fun `given all categories, when no filter and empty name, then returns all categories`() = runTest {
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(food, salary)))

        val result = categoryLister.list(null, "").first()

        assertTrue(result is Outcome.Success)
        assertEquals(2, (result as Outcome.Success).value.size)
    }

    @Test
    fun `given income and expense categories, when filter is income, then returns only income categories`() = runTest {
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(food, salary)))

        val result = categoryLister.list(CategoryType.INCOME, "").first()

        assertTrue(result is Outcome.Success)
        assertEquals(1, (result as Outcome.Success).value.size)
        assertEquals(CategoryType.INCOME, (result as Outcome.Success).value.first().type)
    }

    @Test
    fun `given income and expense categories, when filter is expense, then returns expense only`() = runTest {
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(food, salary)))

        val result = categoryLister.list(CategoryType.EXPENSE, "").first()

        assertTrue(result is Outcome.Success)
        assertEquals(1, (result as Outcome.Success).value.size)
        assertEquals(CategoryType.EXPENSE, (result as Outcome.Success).value.first().type)
    }

    @Test
    fun `given categories, when name matches case-insensitively, then returns matching categories`() = runTest {
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(food, salary)))

        val result = categoryLister.list(null, "ali").first()

        assertTrue(result is Outcome.Success)
        assertEquals(1, (result as Outcome.Success).value.size)
        assertEquals("Alimentación", (result as Outcome.Success).value.first().name)
    }

    @Test
    fun `given income filter and name, then returns only income categories matching the name`() = runTest {
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        val rent = CategoryBuilder().name("Alquiler").type(CategoryType.INCOME).build()
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(food, salary, rent)))

        val result = categoryLister.list(CategoryType.INCOME, "alq").first()

        assertTrue(result is Outcome.Success)
        assertEquals(1, (result as Outcome.Success).value.size)
        assertEquals("Alquiler", (result as Outcome.Success).value.first().name)
    }

    @Test
    fun `given categories, when name matches no name, then returns empty list`() = runTest {
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        every { categoryRepository.getAll() } returns flowOf(Outcome.Success(listOf(food, salary)))

        val result = categoryLister.list(null, "transporte").first()

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.isEmpty())
    }
}
