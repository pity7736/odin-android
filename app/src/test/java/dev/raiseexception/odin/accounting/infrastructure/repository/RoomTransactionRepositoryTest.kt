package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class RoomTransactionRepositoryTest {

    private lateinit var database: OdinDatabase
    private lateinit var repository: RoomTransactionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomTransactionRepository(database.transactionDao(), database.tagDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given a regular expense, when finding it, then isTransfer is false`() = runTest {
        seedAccountsAndCategories()
        database.transactionDao().insert(transaction("exp-1", "EXPENSE", "acc-savings", "cat-food"))
        val result = repository.findById("exp-1").first()
        assertTrue(result is Outcome.Success)
        assertEquals(false, (result as Outcome.Success).value.isTransfer)
    }

    @Test
    fun `given the expense side of a transfer, when finding it, then isTransfer is true`() = runTest {
        seedAccountsAndCategories()
        database.transactionDao().insert(transaction("exp-1", "EXPENSE", "acc-savings", "cat-transfer"))
        database.transactionDao().insert(transaction("inc-1", "INCOME", "acc-cash", "cat-transfer"))
        database.transferDao().insert(
            TransferEntity(id = "tr-1", expenseId = "exp-1", incomeId = "inc-1", createdAt = "2026-08-01T10:00:00Z")
        )
        val result = repository.findById("exp-1").first()
        assertTrue(result is Outcome.Success)
        assertEquals(true, (result as Outcome.Success).value.isTransfer)
    }

    @Test
    fun `given an income, when finding it, then isTransfer is false`() = runTest {
        seedAccountsAndCategories()
        database.transactionDao().insert(transaction("inc-1", "INCOME", "acc-savings", "cat-salary"))
        val result = repository.findById("inc-1").first()
        assertTrue(result is Outcome.Success)
        assertEquals(false, (result as Outcome.Success).value.isTransfer)
    }

    @Test
    fun `given an income on a credit card, when finding it, then the account type is credit card`() = runTest {
        seedAccountsAndCategories()
        database.transactionDao().insert(transaction("inc-1", "INCOME", "acc-visa", "cat-transfer"))
        val result = repository.findById("inc-1").first()
        assertTrue(result is Outcome.Success)
        assertEquals(AccountType.CREDIT_CARD, (result as Outcome.Success).value.accountType)
    }

    @Test
    fun `given an expense on a savings account, when finding it, then the account type is savings`() = runTest {
        seedAccountsAndCategories()
        database.transactionDao().insert(transaction("exp-1", "EXPENSE", "acc-savings", "cat-food"))
        val result = repository.findById("exp-1").first()
        assertTrue(result is Outcome.Success)
        assertEquals(AccountType.SAVINGS, (result as Outcome.Success).value.accountType)
    }

    @Test
    fun `given no transaction with the id, when finding it, then returns NotFound`() = runTest {
        val result = repository.findById("missing").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
    }

    private suspend fun seedAccountsAndCategories() {
        this.database.accountDao().insert(AccountBuilder().id("acc-savings").name("Ahorros").build().toEntity())
        this.database.accountDao().insert(AccountBuilder().id("acc-cash").name("Efectivo").build().toEntity())
        this.database.accountDao().insert(
            AccountBuilder()
                .id("acc-visa")
                .name("Visa")
                .creditCard(
                    creditLimit = Money.of(BigDecimal("3000000.00"), Currency.COP),
                    initialDebt = Money.of(BigDecimal("500000.00"), Currency.COP)
                )
                .build()
                .toEntity()
        )
        this.database.categoryDao().insert(this.category("cat-food", "Alimentación", "EXPENSE"))
        this.database.categoryDao().insert(this.category("cat-salary", "Salario", "INCOME"))
        this.database.categoryDao().insert(this.category("cat-transfer", "Transferencia", "TRANSFER"))
    }

    private fun category(id: String, name: String, type: String) = CategoryEntity(
        id = id,
        name = name,
        type = type,
        description = "",
        color = "#FF0000",
        createdAt = "2026-01-01T00:00:00Z"
    )

    private fun transaction(id: String, type: String, accountId: String, categoryId: String) = TransactionEntity(
        id = id,
        type = type,
        accountId = accountId,
        amount = "500.00",
        currency = "COP",
        date = "2026-08-01",
        categoryId = categoryId,
        description = "",
        createdAt = "2026-08-01T10:00:00Z"
    )

    @Test
    fun `given a tagged expense, when finding it, then the detail carries its tags sorted alphabetically`() =
        runTest {
            seedAccountsAndCategories()
            database.transactionDao().insert(transaction("exp-1", "EXPENSE", "acc-savings", "cat-food"))
            seedTag("tag-toby", "Toby")
            seedTag("tag-comida", "comida")
            seedTag("tag-alamo", "Álamo")
            link("exp-1", "tag-toby", "tag-comida", "tag-alamo")
            val detail = (repository.findById("exp-1").first() as Outcome.Success).value
            assertEquals(listOf("Álamo", "comida", "Toby"), detail.tags.map { it.name })
            assertEquals(listOf("tag-alamo", "tag-comida", "tag-toby"), (detail.transaction as Expense).tagIds)
        }

    @Test
    fun `given an expense tagged with ñ, when finding it, then ñ is sorted right after n`() = runTest {
        seedAccountsAndCategories()
        database.transactionDao().insert(transaction("exp-1", "EXPENSE", "acc-savings", "cat-food"))
        seedTag("tag-mozo", "mozo")
        seedTag("tag-moño", "Moño")
        seedTag("tag-mono", "mono")
        link("exp-1", "tag-mozo", "tag-moño", "tag-mono")
        val detail = (repository.findById("exp-1").first() as Outcome.Success).value
        assertEquals(listOf("mono", "Moño", "mozo"), detail.tags.map { it.name })
    }

    @Test
    fun `given an untagged expense, when finding it, then the detail has no tags`() = runTest {
        seedAccountsAndCategories()
        database.transactionDao().insert(transaction("exp-1", "EXPENSE", "acc-savings", "cat-food"))
        val detail = (repository.findById("exp-1").first() as Outcome.Success).value
        assertTrue(detail.tags.isEmpty())
    }

    @Test
    fun `given an income, when finding it, then the detail has no tags`() = runTest {
        seedAccountsAndCategories()
        database.transactionDao().insert(transaction("inc-1", "INCOME", "acc-savings", "cat-salary"))
        val detail = (repository.findById("inc-1").first() as Outcome.Success).value
        assertTrue(detail.tags.isEmpty())
    }

    @Test
    fun `given an expense being observed, when its tags change, then the detail emits again with the new tags`() =
        runTest {
            seedAccountsAndCategories()
            database.transactionDao().insert(transaction("exp-1", "EXPENSE", "acc-savings", "cat-food"))
            seedTag("tag-nala", "Nala")
            repository.findById("exp-1").test {
                assertTrue((awaitItem() as Outcome.Success).value.tags.isEmpty())
                link("exp-1", "tag-nala")
                val updated = (awaitItem() as Outcome.Success).value
                assertEquals(listOf("Nala"), updated.tags.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun seedTag(id: String, name: String) {
        this.database.tagDao().insert(TagEntity(id, name, Tag.normalize(name), "2026-01-01T00:00:00Z"))
    }

    private suspend fun link(expenseId: String, vararg tagIds: String) {
        this.database.expenseTagDao().insertAll(tagIds.map { ExpenseTagEntity(expenseId = expenseId, tagId = it) })
    }
}
