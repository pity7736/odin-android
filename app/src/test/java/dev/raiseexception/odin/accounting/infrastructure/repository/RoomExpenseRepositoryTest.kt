package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.ExpenseBuilder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class RoomExpenseRepositoryTest {

    private lateinit var database: OdinDatabase
    private lateinit var repository: RoomExpenseRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomExpenseRepository(database.transactionDao(), database.expenseTagDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given an expense, when added and read back, then stored as EXPENSE type with correct data`() = runTest {
        val account = AccountBuilder().id("acc-1").build()
        database.accountDao().insert(account.toEntity())
        val category = CategoryEntity(
            id = "cat-1",
            name = "Alimentación",
            type = "EXPENSE",
            description = "",
            color = "#FF0000",
            createdAt = "2026-01-01T00:00:00Z"
        )
        database.categoryDao().insert(category)
        val expense = Expense.restore(
            id = "exp-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("500.00"), Currency.COP),
            date = LocalDate.parse("2026-08-01"),
            categoryId = "cat-1",
            description = "Mercado",
            createdAt = Instant.parse("2026-08-01T10:00:00Z"),
            tagIds = emptyList(),
            gmfTax = null
        )
        val result = repository.add(expense)
        assertTrue(result is Outcome.Success)
        val accountWithTransactions = database.accountDao().findByIdWithTransactions("acc-1").first()!!
        assertEquals(1, accountWithTransactions.transactions.size)
        val stored = accountWithTransactions.transactions.first().transaction
        assertEquals("EXPENSE", stored.type)
        assertEquals("exp-1", stored.id)
        assertEquals("500.00", stored.amount)
    }

    @Test
    fun `given a stored expense, when updating, then the row has the new values keeping id account type createdAt`() =
        runTest {
            val account = AccountBuilder().id("acc-1").build()
            database.accountDao().insert(account.toEntity())
            database.categoryDao().insert(expenseCategory("cat-1", "Alimentación"))
            database.categoryDao().insert(expenseCategory("cat-2", "Restaurantes"))
            val original = Expense.restore(
                id = "exp-1",
                accountId = "acc-1",
                amount = Money.of(BigDecimal("500.00"), Currency.COP),
                date = LocalDate.parse("2026-08-01"),
                categoryId = "cat-1",
                description = "Mercado",
                createdAt = Instant.parse("2026-08-01T10:00:00Z"),
                tagIds = emptyList(),
                gmfTax = null
            )
            repository.add(original)
            val edited = Expense.restore(
                id = "exp-1",
                accountId = "acc-1",
                amount = Money.of(BigDecimal("750.50"), Currency.COP),
                date = LocalDate.parse("2026-08-03"),
                categoryId = "cat-2",
                description = "Cena",
                createdAt = Instant.parse("2026-08-01T10:00:00Z"),
                tagIds = emptyList(),
                gmfTax = null
            )
            val result = repository.update(edited)
            assertTrue(result is Outcome.Success)
            val accountWithTransactions = database.accountDao().findByIdWithTransactions("acc-1").first()!!
            val stored = accountWithTransactions.transactions.single().transaction
            assertEquals("exp-1", stored.id)
            assertEquals("acc-1", stored.accountId)
            assertEquals("EXPENSE", stored.type)
            assertEquals("2026-08-01T10:00:00Z", stored.createdAt)
            assertEquals("750.50", stored.amount)
            assertEquals("2026-08-03", stored.date)
            assertEquals("cat-2", stored.categoryId)
            assertEquals("Cena", stored.description)
        }

    private fun expenseCategory(id: String, name: String) = CategoryEntity(
        id = id,
        name = name,
        type = "EXPENSE",
        description = "",
        color = "#FF0000",
        createdAt = "2026-01-01T00:00:00Z"
    )

    @Test
    fun `given an expense with tags, when added, then its tag links are stored`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(expense(listOf("tag-nala", "tag-comida")))
        assertEquals(setOf("tag-nala", "tag-comida"), storedTagIds())
    }

    @Test
    fun `given an expense with no tags, when added, then no tag links are stored`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(expense(emptyList()))
        assertTrue(storedTagIds().isEmpty())
    }

    @Test
    fun `given an untagged expense, when updated adding tags, then the links are stored`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(expense(emptyList()))
        repository.update(expense(listOf("tag-carro", "tag-gasolina")))
        assertEquals(setOf("tag-carro", "tag-gasolina"), storedTagIds())
    }

    @Test
    fun `given a tagged expense, when updated removing one tag, then only the other link remains`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(expense(listOf("tag-nala", "tag-toby")))
        repository.update(expense(listOf("tag-nala")))
        assertEquals(setOf("tag-nala"), storedTagIds())
    }

    @Test
    fun `given a tagged expense, when updated removing every tag, then no links remain`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(expense(listOf("tag-comida")))
        repository.update(expense(emptyList()))
        assertTrue(storedTagIds().isEmpty())
    }

    @Test
    fun `given a tagged expense, when deleting it, then its row and its tag links are removed`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(expense(listOf("tag-nala", "tag-comida")))
        val result = repository.delete("exp-1")
        assertTrue(result is Outcome.Success)
        val accountWithTransactions = database.accountDao().findByIdWithTransactions("acc-1").first()!!
        assertTrue(accountWithTransactions.transactions.isEmpty())
        assertTrue(database.tagDao().findByExpenseId("exp-1").first().isEmpty())
    }

    @Test
    fun `given an untagged expense, when deleting it, then its row is removed`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(expense(emptyList()))
        val result = repository.delete("exp-1")
        assertTrue(result is Outcome.Success)
        val accountWithTransactions = database.accountDao().findByIdWithTransactions("acc-1").first()!!
        assertTrue(accountWithTransactions.transactions.isEmpty())
    }

    @Test
    fun `given the storage fails, when deleting an expense, then fails with a storage error`() = runTest {
        val failingExpenseTagDao = mockk<ExpenseTagDao>()
        coEvery { failingExpenseTagDao.deleteByGmfMovementId(any()) } throws SQLiteException("disk I/O error")
        val failingRepository = RoomExpenseRepository(database.transactionDao(), failingExpenseTagDao)
        val result = failingRepository.delete("exp-1")
        assertTrue((result as Outcome.Failure).error is StorageError)
        assertEquals("disk I/O error", result.error.internalMessage)
    }

    @Test
    fun `given Mercado with its tax, when added, then the tax is its own row pointing to Mercado with its tags`() =
        runTest {
            seedAccountCategoryAndTags()
            repository.add(mercado(listOf("tag-nala"), tax("400.00")))
            val rows = storedRows()
            val mercadoRow = rows.single { it.transaction.id == "exp-1" }
            val taxRow = rows.single { it.transaction.id == "tax-1" }
            assertNull(mercadoRow.transaction.gmfMovementId)
            assertEquals(listOf("tag-nala"), mercadoRow.tagIds)
            assertEquals("exp-1", taxRow.transaction.gmfMovementId)
            assertEquals("EXPENSE", taxRow.transaction.type)
            assertEquals("400.00", taxRow.transaction.amount)
            assertEquals(listOf("tag-4x1000"), taxRow.tagIds)
        }

    @Test
    fun `given Mercado with its tax, when updated with a recalculated tax, then the stored tax is replaced`() =
        runTest {
            seedAccountCategoryAndTags()
            repository.add(mercado(emptyList(), tax("400.00")))
            repository.update(mercado(emptyList(), tax("200.00")))
            val taxRow = storedRows().single { it.transaction.id == "tax-1" }
            assertEquals("200.00", taxRow.transaction.amount)
            assertEquals("exp-1", taxRow.transaction.gmfMovementId)
            assertEquals(listOf("tag-4x1000"), taxRow.tagIds)
        }

    @Test
    fun `given Mercado with its tax, when updated without a tax, then the stored tax and its links are removed`() =
        runTest {
            seedAccountCategoryAndTags()
            repository.add(mercado(emptyList(), tax("0.40")))
            repository.update(mercado(emptyList(), null))
            assertEquals(listOf("exp-1"), storedRows().map { it.transaction.id })
            assertTrue(database.tagDao().findByExpenseId("tax-1").first().isEmpty())
        }

    @Test
    fun `given an expense that never had a tax, when updated, then no tax row appears`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(mercado(emptyList(), null))
        repository.update(mercado(listOf("tag-nala"), null))
        assertEquals(listOf("exp-1"), storedRows().map { it.transaction.id })
    }

    @Test
    fun `given Mercado with its tax, when deleting Mercado, then both rows and all their links are removed`() =
        runTest {
            seedAccountCategoryAndTags()
            repository.add(mercado(listOf("tag-nala"), tax("400.00")))
            val result = repository.delete("exp-1")
            assertTrue(result is Outcome.Success)
            assertTrue(storedRows().isEmpty())
            assertTrue(database.tagDao().findByExpenseId("tax-1").first().isEmpty())
        }

    @Test
    fun `given Mercado with its tax in the period, when finding spending, then the tax is its own row`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(mercado(emptyList(), tax("400.00")))
        val spending = spendingBetween("2026-10-01", "2026-10-07")
        assertEquals(setOf("exp-1", "tax-1"), spending.map { it.id }.toSet())
        assertEquals("cat-1", spending.single { it.id == "tax-1" }.categoryId)
    }

    private fun mercado(tagIds: List<String>, gmfTax: Expense?): Expense = ExpenseBuilder()
        .id("exp-1")
        .accountId("acc-1")
        .amount("100000.00")
        .date("2026-10-03")
        .description("Mercado")
        .tagIds(tagIds)
        .gmfTax(gmfTax)
        .build()

    private fun tax(amount: String): Expense = ExpenseBuilder()
        .id("tax-1")
        .accountId("acc-1")
        .amount(amount)
        .date("2026-10-03")
        .description("4x1000 – Mercado")
        .tagIds(listOf("tag-4x1000"))
        .build()

    private suspend fun storedRows(): List<TransactionWithTagIds> =
        database.accountDao().findByIdWithTransactions("acc-1").first()!!.transactions

    private suspend fun seedAccountCategoryAndTags() {
        database.accountDao().insert(AccountBuilder().id("acc-1").build().toEntity())
        database.categoryDao().insert(expenseCategory("cat-1", "Perros"))
        listOf("tag-nala", "tag-comida", "tag-toby", "tag-carro", "tag-gasolina", "tag-4x1000").forEach { tagId ->
            database.tagDao().insert(TagEntity(tagId, tagId, tagId, "2026-01-01T00:00:00Z"))
        }
    }

    private fun expense(tagIds: List<String>): Expense = Expense.restore(
        id = "exp-1",
        accountId = "acc-1",
        amount = Money.of(BigDecimal("500.00"), Currency.COP),
        date = LocalDate.parse("2026-08-01"),
        categoryId = "cat-1",
        description = "",
        createdAt = Instant.parse("2026-08-01T10:00:00Z"),
        tagIds = tagIds,
        gmfTax = null
    )

    private suspend fun storedTagIds(): Set<String> {
        val accountWithTransactions = database.accountDao().findByIdWithTransactions("acc-1").first()!!
        return accountWithTransactions.transactions.single().tagIds.toSet()
    }

    @Test
    fun `given an expense dated inside the period, when finding spending, then returns it with its tag ids`() =
        runTest {
            seedAccountCategoryAndTags()
            repository.add(ExpenseBuilder().id("exp-in").date("2026-10-03").tagIds(listOf("tag-nala")).build())
            val spending = spendingBetween("2026-10-01", "2026-10-07")
            assertEquals(listOf("exp-in"), spending.map { it.id })
            assertEquals(listOf("tag-nala"), spending.single().tagIds)
        }

    @Test
    fun `given expenses dated on the first and last day, when finding spending, then returns both`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(ExpenseBuilder().id("exp-start").date("2026-09-01").build())
        repository.add(ExpenseBuilder().id("exp-end").date("2026-09-30").build())
        val spending = spendingBetween("2026-09-01", "2026-09-30")
        assertEquals(setOf("exp-start", "exp-end"), spending.map { it.id }.toSet())
    }

    @Test
    fun `given expenses the day before and the day after, when finding spending, then excludes both`() = runTest {
        seedAccountCategoryAndTags()
        repository.add(ExpenseBuilder().id("exp-before").date("2026-08-31").build())
        repository.add(ExpenseBuilder().id("exp-after").date("2026-10-01").build())
        repository.add(ExpenseBuilder().id("exp-kept").date("2026-09-15").build())
        val spending = spendingBetween("2026-09-01", "2026-09-30")
        assertEquals(listOf("exp-kept"), spending.map { it.id })
    }

    @Test
    fun `given an income in the period, when finding spending, then excludes it`() = runTest {
        seedAccountCategoryAndTags()
        database.transactionDao().insert(incomeEntity("inc-1", "acc-1", "2026-10-02"))
        repository.add(ExpenseBuilder().id("exp-kept").date("2026-10-02").build())
        val spending = spendingBetween("2026-10-01", "2026-10-07")
        assertEquals(listOf("exp-kept"), spending.map { it.id })
    }

    @Test
    fun `given a transfer between money accounts, when finding spending, then excludes its expense leg`() = runTest {
        seedAccountCategoryAndTags()
        database.accountDao().insert(AccountBuilder().id("acc-cash").name("Efectivo").build().toEntity())
        repository.add(ExpenseBuilder().id("exp-transfer").date("2026-10-02").build())
        database.transactionDao().insert(incomeEntity("inc-transfer", "acc-cash", "2026-10-02"))
        linkTransfer("exp-transfer", "inc-transfer")
        repository.add(ExpenseBuilder().id("exp-kept").date("2026-10-02").build())
        val spending = spendingBetween("2026-10-01", "2026-10-07")
        assertEquals(listOf("exp-kept"), spending.map { it.id })
    }

    @Test
    fun `given a credit card payment, when finding spending, then excludes its expense leg`() = runTest {
        seedAccountCategoryAndTags()
        seedCreditCard()
        repository.add(ExpenseBuilder().id("exp-payment").date("2026-10-05").build())
        database.transactionDao().insert(incomeEntity("inc-payment", "card-1", "2026-10-05"))
        linkTransfer("exp-payment", "inc-payment")
        repository.add(ExpenseBuilder().id("exp-kept").date("2026-10-05").build())
        val spending = spendingBetween("2026-10-01", "2026-10-07")
        assertEquals(listOf("exp-kept"), spending.map { it.id })
    }

    @Test
    fun `given a credit card purchase, when finding spending, then includes it on its own date`() = runTest {
        seedAccountCategoryAndTags()
        seedCreditCard()
        repository.add(ExpenseBuilder().id("exp-card").accountId("card-1").date("2026-10-03").build())
        val spending = spendingBetween("2026-10-01", "2026-10-07")
        assertEquals("card-1", spending.single().accountId)
        assertEquals(LocalDate.parse("2026-10-03"), spending.single().date)
    }

    @Test
    fun `given spending being observed, when an expense in the period is added, then emits again with it`() =
        runTest {
            seedAccountCategoryAndTags()
            repository.add(ExpenseBuilder().id("exp-first").date("2026-10-02").build())
            repository.findSpendingBetween(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07")).test {
                assertEquals(listOf("exp-first"), (awaitItem() as Outcome.Success).value.map { it.id })
                repository.add(ExpenseBuilder().id("exp-second").date("2026-10-04").build())
                val updated = (awaitItem() as Outcome.Success).value
                assertEquals(setOf("exp-first", "exp-second"), updated.map { it.id }.toSet())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given the storage fails, when finding spending, then fails with a storage error`() = runTest {
        val failingTransactionDao = mockk<TransactionDao>()
        every { failingTransactionDao.findSpendingBetween(any(), any()) } returns flow {
            throw SQLiteException("disk I/O error")
        }
        val failingRepository = RoomExpenseRepository(failingTransactionDao, database.expenseTagDao())
        val result = failingRepository.findSpendingBetween(
            LocalDate.parse("2026-10-01"),
            LocalDate.parse("2026-10-07")
        ).first()
        assertTrue((result as Outcome.Failure).error is StorageError)
        assertEquals("disk I/O error", result.error.internalMessage)
    }

    private suspend fun spendingBetween(start: String, end: String): List<Expense> {
        val outcome = repository.findSpendingBetween(LocalDate.parse(start), LocalDate.parse(end)).first()
        return (outcome as Outcome.Success).value
    }

    private suspend fun seedCreditCard() {
        val creditCard = AccountBuilder()
            .id("card-1")
            .name("Visa")
            .creditCard(
                creditLimit = Money.of(BigDecimal("5000000.00"), Currency.COP),
                initialDebt = Money.of(BigDecimal("0.00"), Currency.COP)
            )
            .build()
        database.accountDao().insert(creditCard.toEntity())
    }

    private fun incomeEntity(id: String, accountId: String, date: String) = TransactionEntity(
        id = id,
        type = "INCOME",
        accountId = accountId,
        amount = "500.00",
        currency = "COP",
        date = date,
        categoryId = "cat-1",
        description = "",
        createdAt = "2026-10-01T10:00:00Z"
    )

    private suspend fun linkTransfer(expenseId: String, incomeId: String) {
        database.transferDao().insert(
            TransferEntity(
                id = "tr-$expenseId",
                expenseId = expenseId,
                incomeId = incomeId,
                createdAt = "2026-10-01T10:00:00Z"
            )
        )
    }
}
