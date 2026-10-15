package dev.raiseexception.odin.accounting.presentation.categorycreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CreateCategoryViewModelTest {

    private val categoryCreator = mockk<CategoryCreator>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CreateCategoryViewModel

    private val expenseCategory = (
        Category.create(
            name = "Alimentación",
            type = CategoryType.EXPENSE,
            description = "Gastos de comida",
            color = "#E57373"
        ) as Outcome.Success
        ).value

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CreateCategoryViewModel(categoryCreator, failureRecorder)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given initial state, when observed, then emits Idle`() = runTest {
        viewModel.uiState.test {
            assertEquals(CreateCategoryUiState.Idle, awaitItem())
        }
    }

    @Test
    fun `given valid input, when creating, then emits Loading then navigates to CategoriesList`() = runTest {
        coEvery { categoryCreator.create(any(), any(), any(), any()) } returns Outcome.Success(expenseCategory)
        viewModel.create("Alimentación", CategoryType.EXPENSE, "Gastos de comida", null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.CategoriesList, viewModel.navigationEvent.first())
    }

    @Test
    fun `given Loading state, when create is called again, then second call is ignored`() = runTest {
        coEvery { categoryCreator.create(any(), any(), any(), any()) } returns Outcome.Success(expenseCategory)

        viewModel.create("Alimentación", CategoryType.EXPENSE, "", null)
        viewModel.create("Alimentación", CategoryType.EXPENSE, "", null)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { categoryCreator.create(any(), any(), any(), any()) }
    }

    @Test
    fun `given invalid input, when creating, then emits ValidationError with field errors`() = runTest {
        coEvery { categoryCreator.create(any(), any(), any(), any()) } returns Outcome.Failure(
            CategoryCreationError.InvalidInput(
                nameError = "El nombre es obligatorio.",
                typeError = "El tipo de categoría es obligatorio.",
                descriptionError = null,
                colorError = null
            )
        )
        viewModel.uiState.test {
            assertEquals(CreateCategoryUiState.Idle, awaitItem())
            viewModel.create("", null, "", null)
            assertEquals(CreateCategoryUiState.Loading, awaitItem())
            val error = awaitItem() as CreateCategoryUiState.ValidationError
            assertEquals("El nombre es obligatorio.", error.nameError)
            assertEquals("El tipo de categoría es obligatorio.", error.typeError)
        }
    }

    @Test
    fun `given duplicate name, when creating, then emits ValidationError with nameError`() = runTest {
        coEvery { categoryCreator.create(any(), any(), any(), any()) } returns Outcome.Failure(
            CategoryCreationError.DuplicateName(
                internalMessage = "duplicate",
                externalMessage = "Ya tienes una categoría con ese nombre."
            )
        )
        viewModel.uiState.test {
            assertEquals(CreateCategoryUiState.Idle, awaitItem())
            viewModel.create("Alimentación", CategoryType.EXPENSE, "", null)
            assertEquals(CreateCategoryUiState.Loading, awaitItem())
            val error = awaitItem() as CreateCategoryUiState.ValidationError
            assertEquals("Ya tienes una categoría con ese nombre.", error.nameError)
        }
    }

    @Test
    fun `given crypto failure, when creating, then emits Error with message`() = runTest {
        coEvery { categoryCreator.create(any(), any(), any(), any()) } returns Outcome.Failure(
            CategoryCreationError.CryptoFailure(
                internalMessage = "crypto",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )
        viewModel.uiState.test {
            assertEquals(CreateCategoryUiState.Idle, awaitItem())
            viewModel.create("Alimentación", CategoryType.EXPENSE, "", null)
            assertEquals(CreateCategoryUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is CreateCategoryUiState.Error)
            assertEquals("Algo salió mal. Intente de nuevo más tarde", (state as CreateCategoryUiState.Error).message)
        }
    }

    @Test
    fun `given the save throws, when creating a category, then the prior state is restored and reported`() =
        runTest {
            coEvery { categoryCreator.create(any(), any(), any(), any()) } returns Outcome.Failure(
                CategoryCreationError.DuplicateName(internalMessage = "duplicate", externalMessage = "Ya existe")
            )
            viewModel.create("Alimentación", CategoryType.EXPENSE, "", null)
            testDispatcher.scheduler.advanceUntilIdle()
            coEvery { categoryCreator.create(any(), any(), any(), any()) } throws IllegalStateException("falla")
            viewModel.create("Alimentación", CategoryType.EXPENSE, "", null)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateCategoryUiState.ValidationError(nameError = "Ya existe"), viewModel.uiState.value)
            viewModel.unexpectedFailureEvent.test {
                assertEquals(Unit, awaitItem())
                expectNoEvents()
            }
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given a save that takes 60 seconds, when creating a category, then it completes with no failure`() = runTest {
        coEvery { categoryCreator.create(any(), any(), any(), any()) } coAnswers {
            delay(60.seconds)
            Outcome.Success(expenseCategory)
        }
        viewModel.create("Alimentación", CategoryType.EXPENSE, "", null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.CategoriesList, viewModel.navigationEvent.first())
        verify(exactly = 0) { failureRecorder.record(any()) }
    }

    @Test
    fun `given an expected failure, when creating a category, then it shows as before and is not recorded`() =
        runTest {
            coEvery { categoryCreator.create(any(), any(), any(), any()) } returns Outcome.Failure(
                CategoryCreationError.CryptoFailure(internalMessage = "crypto", externalMessage = "Algo salió mal")
            )
            viewModel.create("Alimentación", CategoryType.EXPENSE, "", null)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateCategoryUiState.Error("Algo salió mal"), viewModel.uiState.value)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }
}
