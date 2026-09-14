package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.CategoryBuilder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryFinderTest {

    private val categoryRepository = mockk<CategoryRepository>()
    private val categoryFinder = CategoryFinder(categoryRepository)

    @Test
    fun `given an existing category, when finding it, then returns it`() = runTest {
        val category = CategoryBuilder().id("cat-123").build()
        every { categoryRepository.findById("cat-123") } returns flowOf(Outcome.Success(category))
        val result = categoryFinder.find("cat-123").first()
        assertTrue(result is Outcome.Success)
        assertEquals(category, (result as Outcome.Success).value)
    }

    @Test
    fun `given a missing category, when finding it, then propagates not found`() = runTest {
        every { categoryRepository.findById("missing") } returns flowOf(
            Outcome.Failure(
                CategoryLookupError.NotFound(
                    internalMessage = "Category with id missing not found",
                    externalMessage = "Categoría no encontrada"
                )
            )
        )
        val result = categoryFinder.find("missing").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CategoryLookupError.NotFound)
    }

    @Test
    fun `given a storage failure, when finding it, then propagates the error`() = runTest {
        every { categoryRepository.findById("any") } returns flowOf(
            Outcome.Failure(StorageError(internalMessage = "Storage error"))
        )
        val result = categoryFinder.find("any").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is StorageError)
    }
}
