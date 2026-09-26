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
        repository = RoomExpenseRepository(database.transactionDao())
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
            createdAt = Instant.parse("2026-08-01T10:00:00Z")
        )
        val result = repository.add(expense)
        assertTrue(result is Outcome.Success)
        val accountWithTransactions = database.accountDao().findByIdWithTransactions("acc-1").first()!!
        assertEquals(1, accountWithTransactions.transactions.size)
        val stored = accountWithTransactions.transactions.first()
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
                createdAt = Instant.parse("2026-08-01T10:00:00Z")
            )
            repository.add(original)
            val edited = Expense.restore(
                id = "exp-1",
                accountId = "acc-1",
                amount = Money.of(BigDecimal("750.50"), Currency.COP),
                date = LocalDate.parse("2026-08-03"),
                categoryId = "cat-2",
                description = "Cena",
                createdAt = Instant.parse("2026-08-01T10:00:00Z")
            )
            val result = repository.update(edited)
            assertTrue(result is Outcome.Success)
            val stored = database.accountDao().findByIdWithTransactions("acc-1").first()!!.transactions.single()
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
}
