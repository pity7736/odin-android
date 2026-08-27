package dev.raiseexception.odin.accounting.presentation.categorieslist

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesListViewModelTest {

    private val categoryLister = mockk<CategoryLister>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = CategoriesListViewModel(categoryLister, testDispatcher)

    private fun expenseCategory(id: String, name: String): Category = Category.restore(
        id = id,
        name = name,
        type = CategoryType.EXPENSE,
        description = "",
        color = "#E57373",
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun incomeCategory(id: String, name: String): Category = Category.restore(
        id = id,
        name = name,
        type = CategoryType.INCOME,
        description = "",
        color = "#4CAF50",
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    @Test
    fun `given CategoryLister returns empty list, when observed, then uiState is Empty`() = runTest {
        every { categoryLister.list(any(), any()) } returns flowOf(emptyList())
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
        val food = expenseCategory("aaa", "Alimentación")
        val salary = incomeCategory("bbb", "Salario")
        every { categoryLister.list(null, "") } returns flowOf(listOf(food, salary))
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
        val food = expenseCategory("aaa", "Alimentación")
        val salary = incomeCategory("bbb", "Salario")
        every { categoryLister.list(null, "") } returns flowOf(listOf(food, salary))
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(listOf(salary))
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
        val food = expenseCategory("aaa", "Alimentación")
        val salary = incomeCategory("bbb", "Salario")
        every { categoryLister.list(null, "") } returns flowOf(listOf(food, salary))
        every { categoryLister.list(null, "ali") } returns flowOf(listOf(food))
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
        val food = expenseCategory("aaa", "Alimentación")
        every { categoryLister.list(null, "") } returns flowOf(listOf(food))
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(emptyList())
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
        val food = expenseCategory("aaa", "Alimentación")
        every { categoryLister.list(any(), any()) } returns flowOf(listOf(food))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            viewModel.onCategorySelected("aaa")
            val event = awaitItem()
            assertTrue(event is CategoriesListNavigationTarget.CategoryDetail)
            assertEquals("aaa", (event as CategoriesListNavigationTarget.CategoryDetail).categoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `given CategoryLister throws, when observed, then uiState is Error with Spanish message`() = runTest {
        every { categoryLister.list(any(), any()) } returns flow { throw RuntimeException("Storage error") }
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(CategoriesListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CategoriesListUiState.Error
            assertEquals("Error al cargar las categorías", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
