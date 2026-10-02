package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
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
            tagIds = emptyList()
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
                tagIds = emptyList()
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
                tagIds = emptyList()
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

    private suspend fun seedAccountCategoryAndTags() {
        database.accountDao().insert(AccountBuilder().id("acc-1").build().toEntity())
        database.categoryDao().insert(expenseCategory("cat-1", "Perros"))
        listOf("tag-nala", "tag-comida", "tag-toby", "tag-carro", "tag-gasolina").forEach { tagId ->
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
        tagIds = tagIds
    )

    private suspend fun storedTagIds(): Set<String> {
        val accountWithTransactions = database.accountDao().findByIdWithTransactions("acc-1").first()!!
        return accountWithTransactions.transactions.single().tagIds.toSet()
    }
}
