package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.CategoryBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryResolverTest {

    private val categoryRepository = mockk<CategoryRepository>()
    private val categoryCreator = mockk<CategoryCreator>()
    private val categoryResolver = CategoryResolver(categoryRepository, categoryCreator)

    @Test
    fun `given an existing expense category, when resolving it as expense, then resolves its id`() = runTest {
        val expenseCategory = CategoryBuilder().id("cat-food").type(CategoryType.EXPENSE).build()
        every { categoryRepository.findById("cat-food") } returns flowOf(Outcome.Success(expenseCategory))
        val result = categoryResolver.resolve(CategoryInput.Existing("cat-food"), CategoryType.EXPENSE)
        assertEquals(Outcome.Success(CategoryResolution.Resolved("cat-food")), result)
    }

    @Test
    fun `given an existing income category, when resolving it as income, then resolves its id`() = runTest {
        val incomeCategory = CategoryBuilder().id("cat-salary").type(CategoryType.INCOME).build()
        every { categoryRepository.findById("cat-salary") } returns flowOf(Outcome.Success(incomeCategory))
        val result = categoryResolver.resolve(CategoryInput.Existing("cat-salary"), CategoryType.INCOME)
        assertEquals(Outcome.Success(CategoryResolution.Resolved("cat-salary")), result)
    }

    @Test
    fun `given an income category, when resolving it as expense, then rejects it as not of type gasto`() = runTest {
        val incomeCategory = CategoryBuilder().id("cat-salary").type(CategoryType.INCOME).build()
        every { categoryRepository.findById("cat-salary") } returns flowOf(Outcome.Success(incomeCategory))
        val result = categoryResolver.resolve(CategoryInput.Existing("cat-salary"), CategoryType.EXPENSE)
        assertEquals(
            Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no es de tipo gasto.")),
            result
        )
    }

    @Test
    fun `given an expense category, when resolving it as income, then rejects it as not of type ingreso`() = runTest {
        val expenseCategory = CategoryBuilder().id("cat-food").type(CategoryType.EXPENSE).build()
        every { categoryRepository.findById("cat-food") } returns flowOf(Outcome.Success(expenseCategory))
        val result = categoryResolver.resolve(CategoryInput.Existing("cat-food"), CategoryType.INCOME)
        assertEquals(
            Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no es de tipo ingreso.")),
            result
        )
    }

    @Test
    fun `given an income category, when resolving it as transfer, then rejects it as not of type transferencia`() =
        runTest {
            val incomeCategory = CategoryBuilder().id("cat-salary").type(CategoryType.INCOME).build()
            every { categoryRepository.findById("cat-salary") } returns flowOf(Outcome.Success(incomeCategory))
            val result = categoryResolver.resolve(CategoryInput.Existing("cat-salary"), CategoryType.TRANSFER)
            assertEquals(
                Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no es de tipo transferencia.")),
                result
            )
        }

    @Test
    fun `given an unknown category id, when resolving it, then rejects it as not existing`() = runTest {
        every { categoryRepository.findById("cat-gone") } returns flowOf(
            Outcome.Failure(CategoryLookupError.NotFound("Category not found", "Categoría no encontrada"))
        )
        val result = categoryResolver.resolve(CategoryInput.Existing("cat-gone"), CategoryType.INCOME)
        assertEquals(Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no existe.")), result)
    }

    @Test
    fun `given a new valid name, when resolving it as income, then creates an income category and resolves it`() =
        runTest {
            val createdCategory = CategoryBuilder().id("cat-bonus").type(CategoryType.INCOME).build()
            coEvery { categoryCreator.create("Bonos", CategoryType.INCOME, "", null) } returns
                Outcome.Success(createdCategory)
            val result = categoryResolver.resolve(CategoryInput.New("Bonos"), CategoryType.INCOME)
            assertEquals(Outcome.Success(CategoryResolution.Resolved("cat-bonus")), result)
            coVerify { categoryCreator.create("Bonos", CategoryType.INCOME, "", null) }
        }

    @Test
    fun `given a new valid name, when resolving it as expense, then creates an expense category and resolves it`() =
        runTest {
            val createdCategory = CategoryBuilder().id("cat-transport").type(CategoryType.EXPENSE).build()
            coEvery { categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null) } returns
                Outcome.Success(createdCategory)
            val result = categoryResolver.resolve(CategoryInput.New("Transporte"), CategoryType.EXPENSE)
            assertEquals(Outcome.Success(CategoryResolution.Resolved("cat-transport")), result)
        }

    @Test
    fun `given a blank new name, when resolving it, then rejects it with the creator name message`() = runTest {
        coEvery { categoryCreator.create("", CategoryType.INCOME, "", null) } returns Outcome.Failure(
            CategoryCreationError.InvalidInput(
                nameError = "El nombre es obligatorio.",
                typeError = null,
                descriptionError = null
            )
        )
        val result = categoryResolver.resolve(CategoryInput.New(""), CategoryType.INCOME)
        assertEquals(Outcome.Success(CategoryResolution.Rejected("El nombre es obligatorio.")), result)
    }

    @Test
    fun `given a duplicate new name, when resolving it, then rejects it with the duplicate message`() = runTest {
        coEvery { categoryCreator.create("Salario", CategoryType.INCOME, "", null) } returns Outcome.Failure(
            CategoryCreationError.DuplicateName(
                internalMessage = "Duplicate",
                externalMessage = "Ya tienes una categoría de tipo ingreso con ese nombre."
            )
        )
        val result = categoryResolver.resolve(CategoryInput.New("Salario"), CategoryType.INCOME)
        assertEquals(
            Outcome.Success(CategoryResolution.Rejected("Ya tienes una categoría de tipo ingreso con ese nombre.")),
            result
        )
    }

    @Test
    fun `given the category lookup fails with a storage error, when resolving, then returns that failure`() =
        runTest {
            val storageError = StorageError("category read error")
            every { categoryRepository.findById("cat-salary") } returns flowOf(Outcome.Failure(storageError))
            val result = categoryResolver.resolve(CategoryInput.Existing("cat-salary"), CategoryType.INCOME)
            assertTrue(result is Outcome.Failure)
            assertEquals(storageError, (result as Outcome.Failure).error)
        }

    @Test
    fun `given category creation fails with a storage error, when resolving, then returns that failure`() =
        runTest {
            val storageError = StorageError("category write error")
            coEvery { categoryCreator.create("Bonos", CategoryType.INCOME, "", null) } returns
                Outcome.Failure(storageError)
            val result = categoryResolver.resolve(CategoryInput.New("Bonos"), CategoryType.INCOME)
            assertTrue(result is Outcome.Failure)
            assertEquals(storageError, (result as Outcome.Failure).error)
        }
}
