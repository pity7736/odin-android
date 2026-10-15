package dev.raiseexception.odin.accounting.presentation.categorieslist

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesListViewModelTest {

    private val categoryLister = mockk<CategoryLister>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = CategoriesListViewModel(categoryLister, failureRecorder, testDispatcher)

    private val storageError = object : DomainError {
        override val internalMessage = "Storage error"
        override val externalMessage = "Error interno"
    }

    @Test
    fun `given CategoryLister returns empty list, when observed, then uiState is Empty`() = runTest {
        every { categoryLister.list(any(), any()) } returns flowOf(Outcome.Success(emptyList()))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(CategoriesListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoriesListUiState.Empty(null, ""), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given CategoryLister returns categories, when observed, then uiState is Content`() = runTest {
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        every { categoryLister.list(null, "") } returns flowOf(Outcome.Success(listOf(food, salary)))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(CategoriesListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as CategoriesListUiState.Content
            assertEquals(2, content.categories.size)
            assertEquals(null, content.activeFilter)
            assertEquals("", content.searchQuery)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given Content, when filter changed, then CategoryLister is called with new filter`() = runTest {
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        every { categoryLister.list(null, "") } returns flowOf(Outcome.Success(listOf(food, salary)))
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(Outcome.Success(listOf(salary)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterChanged(CategoryType.INCOME)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val content = awaitItem() as CategoriesListUiState.Content
            assertEquals(1, content.categories.size)
            assertEquals(CategoryType.INCOME, content.activeFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given Content, when search name changed, then CategoryLister is called with new name`() = runTest {
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        every { categoryLister.list(null, "") } returns flowOf(Outcome.Success(listOf(food, salary)))
        every { categoryLister.list(null, "ali") } returns flowOf(Outcome.Success(listOf(food)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("ali")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val content = awaitItem() as CategoriesListUiState.Content
            assertEquals(1, content.categories.size)
            assertEquals("ali", content.searchQuery)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given CategoryLister returns empty list after filter change, then uiState is Empty`() = runTest {
        val food = CategoryBuilder().build()
        every { categoryLister.list(null, "") } returns flowOf(Outcome.Success(listOf(food)))
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(Outcome.Success(emptyList()))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterChanged(CategoryType.INCOME)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(CategoriesListUiState.Empty(CategoryType.INCOME, ""), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given categories, when category selected, then navigation event is CategoryDetail`() = runTest {
        val food = CategoryBuilder().build()
        every { categoryLister.list(any(), any()) } returns flowOf(Outcome.Success(listOf(food)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            viewModel.onCategorySelected(food.id)
            val event = awaitItem()
            assertTrue(event is CategoriesListNavigationTarget.CategoryDetail)
            assertEquals(food.id, (event as CategoriesListNavigationTarget.CategoryDetail).categoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given CategoryLister emits failure, when observed, then uiState is Error with Spanish message`() = runTest {
        every { categoryLister.list(any(), any()) } returns flowOf(Outcome.Failure(storageError))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(CategoriesListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CategoriesListUiState.Error
            assertEquals("Error al cargar las categorías", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given loading throws, when loading the categories, then UnexpectedFailure is shown and recorded`() =
        runTest {
            every { categoryLister.list(any(), any()) } returns flow<Outcome<List<Category>>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoriesListUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given no result within 10 seconds, when loading the categories, then UnexpectedFailure is shown`() =
        runTest {
            every { categoryLister.list(any(), any()) } returns flow<Outcome<List<Category>>> { awaitCancellation() }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceTimeBy(10.seconds)
            testDispatcher.scheduler.runCurrent()
            assertEquals(CategoriesListUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given UnexpectedFailure, when loading the categories again succeeds, then the normal content is shown`() =
        runTest {
            every { categoryLister.list(any(), any()) } returns flow<Outcome<List<Category>>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            every { categoryLister.list(any(), any()) } returns flowOf(Outcome.Success(emptyList()))
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoriesListUiState.Empty(null, ""), viewModel.uiState.value)
        }

    @Test
    fun `given UnexpectedFailure, when loading the categories again fails, then it is shown and recorded twice`() =
        runTest {
            every { categoryLister.list(any(), any()) } returns flow<Outcome<List<Category>>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoriesListUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 2) { failureRecorder.record(any()) }
        }

    @Test
    fun `given an expected failure, when loading the categories, then it shows as before and is not recorded`() =
        runTest {
            every { categoryLister.list(any(), any()) } returns flowOf(Outcome.Failure(storageError))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoriesListUiState.Error("Error al cargar las categorías"), viewModel.uiState.value)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }

    @Test
    fun `given a first result then a failure, when loading the categories, then UnexpectedFailure is shown`() =
        runTest {
            every { categoryLister.list(any(), any()) } returns flow<Outcome<List<Category>>> {
                emit(Outcome.Success(emptyList()))
                error("falla")
            }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CategoriesListUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }
}
