package dev.raiseexception.odin.accounting.presentation.categorydetail

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.CategoryFinder
import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.CategoryBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailViewModelTest {

    private val categoryFinder = mockk<CategoryFinder>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val categoryId = "test-category-id"
    private val bogotaTimeZone = TimeZone.of("America/Bogota")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        CategoryDetailViewModel(categoryId, categoryFinder, failureRecorder, testDispatcher, bogotaTimeZone)

    @Test
    fun `given an existing category, when loaded, then emits loading then content`() = runTest {
        val category = CategoryBuilder()
            .id(categoryId)
            .name("Alimentación")
            .type(CategoryType.EXPENSE)
            .description("Comida y mercado")
            .color("#E57373")
            .createdAt(Instant.parse("2026-09-14T10:00:00Z"))
            .build()
        every { categoryFinder.find(categoryId) } returns flowOf(Outcome.Success(category))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(CategoryDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CategoryDetailUiState.Content
            assertEquals("Alimentación", state.name)
            assertEquals(CategoryType.EXPENSE, state.type)
            assertEquals("Comida y mercado", state.description)
            assertEquals("#E57373", state.color)
            assertEquals("14 de septiembre de 2026", state.formattedCreatedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a category without description, when loaded, then content has empty description`() = runTest {
        val category = CategoryBuilder()
            .id(categoryId)
            .name("Salario")
            .type(CategoryType.INCOME)
            .description("")
            .createdAt(Instant.parse("2026-01-15T08:00:00Z"))
            .build()
        every { categoryFinder.find(categoryId) } returns flowOf(Outcome.Success(category))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(CategoryDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CategoryDetailUiState.Content
            assertEquals("", state.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a category created late at night locally, when loaded, then shows the local date not utc`() = runTest {
        val category = CategoryBuilder()
            .id(categoryId)
            .name("Cena")
            .type(CategoryType.EXPENSE)
            .description("")
            .createdAt(Instant.parse("2026-09-15T04:00:00Z"))
            .build()
        every { categoryFinder.find(categoryId) } returns flowOf(Outcome.Success(category))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(CategoryDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CategoryDetailUiState.Content
            assertEquals("14 de septiembre de 2026", state.formattedCreatedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a missing category, when loaded, then emits loading then not found`() = runTest {
        every { categoryFinder.find(categoryId) } returns flowOf(
            Outcome.Failure(
                CategoryLookupError.NotFound(
                    internalMessage = "Category with id $categoryId not found",
                    externalMessage = "Categoría no encontrada"
                )
            )
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(CategoryDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoryDetailUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a storage error, when loaded, then emits loading then error`() = runTest {
        every { categoryFinder.find(categoryId) } returns flowOf(
            Outcome.Failure(
                StorageError(
                    internalMessage = "Database error",
                    externalMessage = "Error al acceder a los datos"
                )
            )
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(CategoryDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CategoryDetailUiState.Error
            assertEquals("Error al acceder a los datos", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given loading throws, when loading the category, then UnexpectedFailure is shown and recorded`() =
        runTest {
            every { categoryFinder.find(categoryId) } returns flow<Outcome<Category>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoryDetailUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given no result within 10 seconds, when loading the category, then UnexpectedFailure is shown`() =
        runTest {
            every { categoryFinder.find(categoryId) } returns flow<Outcome<Category>> { awaitCancellation() }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceTimeBy(10.seconds)
            testDispatcher.scheduler.runCurrent()
            assertEquals(CategoryDetailUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given UnexpectedFailure, when loading the category again succeeds, then the normal content is shown`() =
        runTest {
            every { categoryFinder.find(categoryId) } returns flow<Outcome<Category>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            every { categoryFinder.find(categoryId) } returns
                flowOf(Outcome.Success(CategoryBuilder().id(categoryId).build()))
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is CategoryDetailUiState.Content)
        }

    @Test
    fun `given UnexpectedFailure, when loading the category again fails, then it is shown and recorded twice`() =
        runTest {
            every { categoryFinder.find(categoryId) } returns flow<Outcome<Category>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoryDetailUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 2) { failureRecorder.record(any()) }
        }

    @Test
    fun `given an expected failure, when loading the category, then it shows as before and is not recorded`() =
        runTest {
            every {
                categoryFinder.find(categoryId)
            } returns
                flowOf(Outcome.Failure(CategoryLookupError.NotFound("Not found", "No existe")))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoryDetailUiState.NotFound, viewModel.uiState.value)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }

    @Test
    fun `given a first result then a failure, when loading the category, then UnexpectedFailure is shown`() =
        runTest {
            every { categoryFinder.find(categoryId) } returns flow<Outcome<Category>> {
                emit(Outcome.Success(CategoryBuilder().id(categoryId).build()))
                error("falla")
            }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoryDetailUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }
}
