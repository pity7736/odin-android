package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.shared.domain.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCreatorTest {

    private val categoryRepository = mockk<CategoryRepository>()
    private val fixedColor = "#E57373"
    private val creator = CategoryCreator(categoryRepository, colorPicker = { fixedColor })

    @Test
    fun `given invalid input, when creating, then returns failure without calling repository`() = runTest {
        val result = creator.create(
            name = "",
            type = CategoryType.EXPENSE,
            description = "",
            color = fixedColor
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CategoryCreationError.InvalidInput)
        coVerify(exactly = 0) { categoryRepository.existsByNameAndType(any(), any()) }
        coVerify(exactly = 0) { categoryRepository.add(any()) }
    }

    @Test
    fun `given valid input and unique name, when creating, then persists and returns success`() = runTest {
        coEvery {
            categoryRepository.existsByNameAndType("Alimentación", CategoryType.EXPENSE)
        } returns Outcome.Success(false)
        coEvery { categoryRepository.add(any()) } returns Outcome.Success(Unit)

        val result = creator.create(
            name = "Alimentación",
            type = CategoryType.EXPENSE,
            description = "Gastos de comida",
            color = fixedColor
        )

        assertTrue(result is Outcome.Success)
        coVerify { categoryRepository.add(any()) }
    }

    @Test
    fun `given a duplicate name, when creating, then returns DuplicateName without calling add`() = runTest {
        coEvery {
            categoryRepository.existsByNameAndType("Alimentación", CategoryType.EXPENSE)
        } returns Outcome.Success(true)

        val result = creator.create(
            name = "Alimentación",
            type = CategoryType.EXPENSE,
            description = "",
            color = fixedColor
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CategoryCreationError.DuplicateName)
        coVerify(exactly = 0) { categoryRepository.add(any()) }
    }

    @Test
    fun `given same name but different type, when creating, then persists and returns success`() = runTest {
        coEvery {
            categoryRepository.existsByNameAndType("Alquiler", CategoryType.INCOME)
        } returns Outcome.Success(false)
        coEvery { categoryRepository.add(any()) } returns Outcome.Success(Unit)

        val result = creator.create(
            name = "Alquiler",
            type = CategoryType.INCOME,
            description = "",
            color = fixedColor
        )

        assertTrue(result is Outcome.Success)
        coVerify { categoryRepository.add(any()) }
    }

    @Test
    fun `given existsByNameAndType fails, when creating, then propagates the failure`() = runTest {
        coEvery {
            categoryRepository.existsByNameAndType("Alimentación", CategoryType.EXPENSE)
        } returns Outcome.Failure(
            CategoryCreationError.CryptoFailure(
                internalMessage = "crypto broke",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )

        val result = creator.create(
            name = "Alimentación",
            type = CategoryType.EXPENSE,
            description = "",
            color = fixedColor
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CategoryCreationError.CryptoFailure)
        coVerify(exactly = 0) { categoryRepository.add(any()) }
    }

    @Test
    fun `given add fails, when creating, then propagates the failure`() = runTest {
        coEvery {
            categoryRepository.existsByNameAndType("Alimentación", CategoryType.EXPENSE)
        } returns Outcome.Success(false)
        coEvery { categoryRepository.add(any()) } returns Outcome.Failure(
            CategoryCreationError.StorageFailure(
                internalMessage = "storage broke",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )

        val result = creator.create(
            name = "Alimentación",
            type = CategoryType.EXPENSE,
            description = "",
            color = fixedColor
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CategoryCreationError.StorageFailure)
    }

    @Test
    fun `given no color provided, when creating, then uses the injected colorPicker`() = runTest {
        coEvery {
            categoryRepository.existsByNameAndType("Alimentación", CategoryType.EXPENSE)
        } returns Outcome.Success(false)
        coEvery { categoryRepository.add(any()) } returns Outcome.Success(Unit)

        val result = creator.create(
            name = "Alimentación",
            type = CategoryType.EXPENSE,
            description = "",
            color = null
        )

        assertTrue(result is Outcome.Success)
        assertEquals(fixedColor, (result as Outcome.Success).value.color)
    }
}
