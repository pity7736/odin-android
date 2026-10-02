package dev.raiseexception.odin.accounting.presentation.expensecreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.application.usecase.TagLister
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.shared.presentation.SelectedTag
import dev.raiseexception.odin.shared.presentation.TagSelection
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.CategoryBuilder
import dev.raiseexception.odin.testutil.TagBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class)
class CreateExpenseViewModelTest {

    private val expenseCreator = mockk<ExpenseCreator>()
    private val categoryLister = mockk<CategoryLister>()
    private val accountFinder = mockk<AccountFinder>()
    private val accountLister = mockk<AccountLister>()
    private val tagLister = mockk<TagLister>()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "acc-1"
    private val accountCreatedAt = Instant.parse("2026-01-01T12:00:00Z")
    private val expenseCategory = CategoryBuilder().type(CategoryType.EXPENSE).build()
    private val account = AccountBuilder().id(accountId).createdAt(accountCreatedAt).build()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { accountFinder.find(accountId) } returns flowOf(Outcome.Success(account))
        every { tagLister.list() } returns flowOf(Outcome.Success(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = CreateExpenseViewModel(
        accountId = accountId,
        expenseCreator = expenseCreator,
        categoryLister = categoryLister,
        accountFinder = accountFinder,
        accountLister = accountLister,
        tagLister = tagLister,
        ioDispatcher = testDispatcher
    )

    @Test
    fun `given account id, when initialized, then loads expense categories and transitions to idle`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )

        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(CreateExpenseUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateExpenseUiState.Idle
            assertEquals(1, state.categories.size)
            assertEquals(expenseCategory.id, state.categories.first().id)
            val expectedDate = accountCreatedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
            assertEquals(expectedDate, state.accountCreatedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid input with existing category, when saving, then navigates back to account detail`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(
                accountId = accountId,
                amount = "500.00",
                date = "2026-08-29",
                categoryInput = CategoryInput.Existing(expenseCategory.id),
                description = "",
                tagInputs = emptyList()
            )
        } returns Outcome.Success(
            dev.raiseexception.odin.accounting.domain.model.Expense.restore(
                id = "exp-1",
                accountId = accountId,
                amount = dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("500.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                ),
                date = LocalDate(2026, 8, 29),
                categoryId = expenseCategory.id,
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z"),
                tagIds = emptyList()
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.AccountDetail(accountId), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid input with new category name, when saving, then navigates back to account detail`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(emptyList())
        )
        coEvery {
            expenseCreator.create(
                accountId = accountId,
                amount = "500.00",
                date = "2026-08-29",
                categoryInput = CategoryInput.New("Transporte"),
                description = "",
                tagInputs = emptyList()
            )
        } returns Outcome.Success(
            dev.raiseexception.odin.accounting.domain.model.Expense.restore(
                id = "exp-1",
                accountId = accountId,
                amount = dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("500.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                ),
                date = LocalDate(2026, 8, 29),
                categoryId = "new-cat-id",
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z"),
                tagIds = emptyList()
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2026-08-29", CategoryInput.New("Transporte"), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.AccountDetail(accountId), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given zero amount, when saving, then shows amount error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = "El monto debe ser mayor que cero.",
                dateError = null,
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("0", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertNotNull(state.amountError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given future date, when saving, then shows date error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = null,
                dateError = "La fecha debe ser hoy o en el pasado.",
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2099-01-01", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertNotNull(state.dateError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given date before account creation, when saving, then shows date error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = null,
                dateError = "La fecha no puede ser anterior a la fecha de creación de la cuenta.",
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2025-12-31", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertNotNull(state.dateError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given missing required field, when saving, then shows field error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = "El monto es obligatorio.",
                dateError = "La fecha es obligatoria.",
                categoryError = "La categoría es obligatoria."
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("", "", CategoryInput.New(""), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertNotNull(state.amountError)
            assertNotNull(state.dateError)
            assertNotNull(state.categoryError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given amount exceeds balance, when saving, then shows amount error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = "El monto supera el saldo disponible.",
                dateError = null,
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("999999.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertNotNull(state.amountError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id, when initialized, then loads accounts for picker`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        val viewModel = CreateExpenseViewModel(
            accountId = null,
            expenseCreator = expenseCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            tagLister = tagLister,
            ioDispatcher = testDispatcher
        )
        viewModel.uiState.test {
            assertEquals(CreateExpenseUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateExpenseUiState.Idle
            assertEquals(1, state.accounts.size)
            assertEquals(accountId, state.accounts.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id, when account selected, then updates selected account`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        val viewModel = CreateExpenseViewModel(
            accountId = null,
            expenseCreator = expenseCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            tagLister = tagLister,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAccountSelected(accountId)
        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.Idle
            assertEquals(accountId, state.selectedAccountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id and no account selected, when saving, then shows account required error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        val viewModel = CreateExpenseViewModel(
            accountId = null,
            expenseCreator = expenseCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            tagLister = tagLister,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertEquals("La cuenta es obligatoria.", state.accountError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id and account selected, when saving, then creates with selected account`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        coEvery {
            expenseCreator.create(
                accountId = accountId,
                amount = "500.00",
                date = "2026-08-29",
                categoryInput = CategoryInput.Existing(expenseCategory.id),
                description = "",
                tagInputs = emptyList()
            )
        } returns Outcome.Success(
            dev.raiseexception.odin.accounting.domain.model.Expense.restore(
                id = "exp-1",
                accountId = accountId,
                amount = dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("500.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                ),
                date = kotlinx.datetime.LocalDate(2026, 8, 29),
                categoryId = expenseCategory.id,
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z"),
                tagIds = emptyList()
            )
        )
        val viewModel = CreateExpenseViewModel(
            accountId = null,
            expenseCreator = expenseCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            tagLister = tagLister,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAccountSelected(accountId)
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.Back, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account id provided, when initialized, then does not load accounts for picker`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(CreateExpenseUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateExpenseUiState.Idle
            assertTrue(state.accounts.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given already saving, when save called again, then ignores duplicate call`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any(), any())
        } returns Outcome.Success(
            dev.raiseexception.odin.accounting.domain.model.Expense.restore(
                id = "exp-1",
                accountId = accountId,
                amount = dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("500.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                ),
                date = LocalDate(2026, 8, 29),
                categoryId = expenseCategory.id,
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z"),
                tagIds = emptyList()
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { expenseCreator.create(any(), any(), any(), any(), any(), any()) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CreateExpenseViewModelTagsTest {

    private val expenseCreator = mockk<ExpenseCreator>()
    private val categoryLister = mockk<CategoryLister>()
    private val accountFinder = mockk<AccountFinder>()
    private val accountLister = mockk<AccountLister>()
    private val tagLister = mockk<TagLister>()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "acc-1"
    private val expenseCategory = CategoryBuilder().type(CategoryType.EXPENSE).build()
    private val account = AccountBuilder().id(accountId).createdAt(Instant.parse("2026-01-01T12:00:00Z")).build()
    private val nalaTag = TagBuilder().id("tag-nala").name("Nala").build()
    private val comidaTag = TagBuilder().id("tag-comida").name("Comida").build()
    private val allTags = listOf(nalaTag, comidaTag)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { accountFinder.find(accountId) } returns flowOf(Outcome.Success(account))
        every { tagLister.list() } returns flowOf(Outcome.Success(allTags))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given account id, when initialized, then Idle carries all tags and an empty selection`() = runTest {
        stubCategories()
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as CreateExpenseUiState.Idle
        assertEquals(allTags, state.tags)
        assertEquals(TagSelection(), state.tagSelection)
    }

    @Test
    fun `given no account id, when initialized, then Idle carries all tags`() = runTest {
        stubCategories()
        every { accountLister.list() } returns flowOf(Outcome.Success(listOf(account)))
        val viewModel = buildHomeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as CreateExpenseUiState.Idle
        assertEquals(allTags, state.tags)
    }

    @Test
    fun `given the tags cannot be listed, when initialized with an account, then shows Error`() = runTest {
        stubCategories()
        every { tagLister.list() } returns flowOf(Outcome.Failure(StorageError("tags error")))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CreateExpenseUiState.Error)
    }

    @Test
    fun `given the tags cannot be listed, when initialized from home, then shows Error`() = runTest {
        stubCategories()
        every { accountLister.list() } returns flowOf(Outcome.Success(listOf(account)))
        every { tagLister.list() } returns flowOf(Outcome.Failure(StorageError("tags error")))
        val viewModel = buildHomeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CreateExpenseUiState.Error)
    }

    @Test
    fun `given Idle, when typing and adding a tag, then the selection holds it`() = runTest {
        stubCategories()
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onTagTextChange("Carro")
        assertEquals("Carro", idleSelection(viewModel).text)
        viewModel.addTag()
        assertEquals(listOf(SelectedTag("Carro", TagInput.New("Carro"))), idleSelection(viewModel).selected)
    }

    @Test
    fun `given Idle, when picking and removing tags, then the selection follows`() = runTest {
        stubCategories()
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.pickTag(nalaTag)
        viewModel.pickTag(comidaTag)
        viewModel.removeTag(0)
        assertEquals(listOf(SelectedTag("Comida", TagInput.Existing("tag-comida"))), idleSelection(viewModel).selected)
    }

    @Test
    fun `given ValidationError, when tag events happen, then the selection in ValidationError is updated`() =
        runTest {
            stubCategories()
            coEvery { expenseCreator.create(any(), any(), any(), any(), any(), any()) } returns amountFailure()
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.save("0", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.pickTag(nalaTag)
            viewModel.onTagTextChange("Carro")
            viewModel.addTag()
            viewModel.pickTag(comidaTag)
            viewModel.removeTag(0)
            val state = viewModel.uiState.value as CreateExpenseUiState.ValidationError
            assertEquals(listOf("Carro", "Comida"), state.tagSelection.selected.map { it.name })
        }

    @Test
    fun `given Saving, when tag events happen, then nothing changes`() = runTest {
        stubCategories()
        val pendingSave = CompletableDeferred<Outcome<Expense>>()
        coEvery { expenseCreator.create(any(), any(), any(), any(), any(), any()) } coAnswers { pendingSave.await() }
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        viewModel.pickTag(nalaTag)
        assertEquals(CreateExpenseUiState.Saving, viewModel.uiState.value)
        pendingSave.complete(Outcome.Success(savedExpense()))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `given added tags, when saving, then the creator receives the selection inputs`() = runTest {
        stubCategories()
        coEvery { expenseCreator.create(any(), any(), any(), any(), any(), any()) } returns
            Outcome.Success(savedExpense())
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.pickTag(nalaTag)
        viewModel.onTagTextChange("Carro")
        viewModel.addTag()
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify {
            expenseCreator.create(
                accountId,
                "500.00",
                "2026-08-29",
                CategoryInput.Existing(expenseCategory.id),
                "",
                listOf(TagInput.Existing("tag-nala"), TagInput.New("Carro"))
            )
        }
    }

    @Test
    fun `given a typed tag not yet added, when saving, then it is saved with the expense`() = runTest {
        stubCategories()
        coEvery { expenseCreator.create(any(), any(), any(), any(), any(), any()) } returns
            Outcome.Success(savedExpense())
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onTagTextChange("Carro")
        viewModel.addTag()
        viewModel.onTagTextChange("Gasolina")
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify {
            expenseCreator.create(
                any(),
                any(),
                any(),
                any(),
                any(),
                listOf(TagInput.New("Carro"), TagInput.New("Gasolina"))
            )
        }
    }

    @Test
    fun `given a typed tag matching one already added, when saving, then it is not repeated`() = runTest {
        stubCategories()
        coEvery { expenseCreator.create(any(), any(), any(), any(), any(), any()) } returns
            Outcome.Success(savedExpense())
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.pickTag(nalaTag)
        viewModel.onTagTextChange("nala")
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { expenseCreator.create(any(), any(), any(), any(), any(), listOf(TagInput.Existing("tag-nala"))) }
    }

    @Test
    fun `given five added tags, when saving, then the expense is saved with the five tags`() = runTest {
        stubCategories()
        coEvery { expenseCreator.create(any(), any(), any(), any(), any(), any()) } returns
            Outcome.Success(savedExpense())
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        listOf(nalaTag, comidaTag).forEach { viewModel.pickTag(it) }
        listOf("Uno", "Dos", "Tres").forEach { name ->
            viewModel.onTagTextChange(name)
            viewModel.addTag()
        }
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { expenseCreator.create(any(), any(), any(), any(), any(), match { it.size == FIVE_TAGS }) }
    }

    @Test
    fun `given a typed tag too long, when saving, then shows the tags error without calling the creator`() = runTest {
        stubCategories()
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onTagTextChange("a".repeat(Tag.MAX_NAME_LENGTH + 1))
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as CreateExpenseUiState.ValidationError
        assertEquals("La etiqueta no puede superar 30 caracteres.", state.tagSelection.error)
        coVerify(exactly = 0) { expenseCreator.create(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given the creator rejects the tags, when saving, then shows its tags error and keeps the tags`() = runTest {
        stubCategories()
        coEvery { expenseCreator.create(any(), any(), any(), any(), any(), any()) } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = null,
                dateError = null,
                categoryError = null,
                tagsError = "La etiqueta no puede superar 30 caracteres."
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.pickTag(nalaTag)
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as CreateExpenseUiState.ValidationError
        assertEquals("La etiqueta no puede superar 30 caracteres.", state.tagSelection.error)
        assertEquals(listOf("Nala"), state.tagSelection.selected.map { it.name })
        assertEquals(allTags, state.tags)
    }

    @Test
    fun `given tags and an amount error, when saving fails, then the tags survive in ValidationError`() = runTest {
        stubCategories()
        coEvery { expenseCreator.create(any(), any(), any(), any(), any(), any()) } returns amountFailure()
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.pickTag(nalaTag)
        viewModel.onTagTextChange("Carro")
        viewModel.save("0", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as CreateExpenseUiState.ValidationError
        assertEquals(listOf("Nala", "Carro"), state.tagSelection.selected.map { it.name })
        assertNull(state.tagSelection.error)
    }

    @Test
    fun `given no account selected and tags, when saving, then the account error keeps the tags`() = runTest {
        stubCategories()
        every { accountLister.list() } returns flowOf(Outcome.Success(listOf(account)))
        val viewModel = buildHomeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.pickTag(nalaTag)
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        val state = viewModel.uiState.value as CreateExpenseUiState.ValidationError
        assertEquals("La cuenta es obligatoria.", state.accountError)
        assertEquals(listOf("Nala"), state.tagSelection.selected.map { it.name })
    }

    private fun buildViewModel() = CreateExpenseViewModel(
        accountId = accountId,
        expenseCreator = expenseCreator,
        categoryLister = categoryLister,
        accountFinder = accountFinder,
        accountLister = accountLister,
        tagLister = tagLister,
        ioDispatcher = testDispatcher
    )

    private fun stubCategories() {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(Outcome.Success(listOf(expenseCategory)))
    }

    private fun buildHomeViewModel() = CreateExpenseViewModel(
        accountId = null,
        expenseCreator = expenseCreator,
        categoryLister = categoryLister,
        accountFinder = accountFinder,
        accountLister = accountLister,
        tagLister = tagLister,
        ioDispatcher = testDispatcher
    )

    private fun idleSelection(viewModel: CreateExpenseViewModel): TagSelection =
        (viewModel.uiState.value as CreateExpenseUiState.Idle).tagSelection

    private fun amountFailure(): Outcome<Expense> = Outcome.Failure(
        ExpenseCreationError.InvalidInput(
            amountError = "El monto debe ser mayor que cero.",
            dateError = null,
            categoryError = null
        )
    )

    private fun savedExpense(): Expense = Expense.restore(
        id = "exp-1",
        accountId = accountId,
        amount = Money.of(BigDecimal("500.00"), Currency.COP),
        date = LocalDate.parse("2026-08-29"),
        categoryId = expenseCategory.id,
        description = "",
        createdAt = Instant.parse("2026-08-29T10:00:00Z"),
        tagIds = emptyList()
    )

    private companion object {
        const val FIVE_TAGS = 5
    }
}
