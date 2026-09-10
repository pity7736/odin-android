package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class RoomAccountRepositoryTest {

    private lateinit var database: OdinDatabase
    private lateinit var repository: RoomAccountRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomAccountRepository(database.accountDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given an account, when added and read back, then all fields match`() = runTest {
        val savings = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .initialBalance(Money.of(BigDecimal("1500.00"), Currency.COP))
            .type(AccountType.SAVINGS)
            .description("Fondo de emergencia")
            .createdAt(Instant.parse("2026-08-01T10:00:00Z"))
            .build()
        repository.add(savings)
        val result = repository.findById("acc-1").first()
        assertTrue(result is Outcome.Success)
        val restored = (result as Outcome.Success).value
        assertEquals("acc-1", restored.id)
        assertEquals("Ahorros", restored.name)
        assertEquals(Money.of(BigDecimal("1500.00"), Currency.COP), restored.initialBalance)
        assertEquals(AccountType.SAVINGS, restored.type)
        assertEquals("Fondo de emergencia", restored.description)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), restored.createdAt)
    }

    @Test
    fun `given an existing name, when checking existsByName, then returns true`() = runTest {
        repository.add(AccountBuilder().name("Ahorros").build())
        val result = repository.existsByName("Ahorros")
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given an existing name in different case, when checking existsByName, then returns true`() = runTest {
        repository.add(AccountBuilder().name("Ahorros").build())
        val result = repository.existsByName("ahorros")
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given no matching name, when checking existsByName, then returns false`() = runTest {
        repository.add(AccountBuilder().name("Ahorros").build())
        val result = repository.existsByName("Corriente")
        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given an account without criteria, when findById, then emits account without transactions`() = runTest {
        val savings = AccountBuilder().id("acc-1").build()
        repository.add(savings)
        val result = repository.findById("acc-1").first()
        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        assertTrue(account.incomes.isEmpty())
        assertTrue(account.expenses.isEmpty())
    }

    @Test
    fun `given account with transactions, when findById with criteria, then splits by type`() =
        runTest {
            val savings = AccountBuilder().id("acc-1").build()
            repository.add(savings)
            val category = CategoryEntity(
                id = "cat-1",
                name = "Salario",
                type = "INCOME",
                description = "",
                color = "#FF0000",
                createdAt = "2026-01-01T00:00:00Z"
            )
            database.categoryDao().insert(category)
            val income = TransactionEntity(
                id = "inc-1",
                type = "INCOME",
                accountId = "acc-1",
                amount = "500.00",
                currency = "COP",
                date = "2026-08-01",
                categoryId = "cat-1",
                description = "Pago",
                createdAt = "2026-08-01T10:00:00Z"
            )
            val expense = TransactionEntity(
                id = "exp-1",
                type = "EXPENSE",
                accountId = "acc-1",
                amount = "200.00",
                currency = "COP",
                date = "2026-08-02",
                categoryId = "cat-1",
                description = "Mercado",
                createdAt = "2026-08-02T10:00:00Z"
            )
            database.transactionDao().insert(income)
            database.transactionDao().insert(expense)
            val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
            val result = repository.findById("acc-1", criteria).first()
            assertTrue(result is Outcome.Success)
            val account = (result as Outcome.Success).value
            assertEquals(1, account.incomes.size)
            assertEquals("inc-1", account.incomes.first().id)
            assertEquals(1, account.expenses.size)
            assertEquals("exp-1", account.expenses.first().id)
        }

    @Test
    fun `given non-existent id, when findById, then emits NotFound`() = runTest {
        val result = repository.findById("non-existent").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountLookupError.NotFound)
    }

    @Test
    fun `given accounts, when getAll without criteria, then returns all without transactions`() = runTest {
        repository.add(AccountBuilder().id("acc-1").name("Ahorros").build())
        repository.add(AccountBuilder().id("acc-2").name("Efectivo").build())
        val result = repository.getAll().first()
        assertTrue(result is Outcome.Success)
        val accounts = (result as Outcome.Success).value
        assertEquals(2, accounts.size)
        assertTrue(accounts.all { it.incomes.isEmpty() && it.expenses.isEmpty() })
    }

    @Test
    fun `given accounts with transactions, when getAll with full criteria, then returns all with transactions`() =
        runTest {
            repository.add(AccountBuilder().id("acc-1").name("Ahorros").build())
            val category = CategoryEntity(
                id = "cat-1",
                name = "Salario",
                type = "INCOME",
                description = "",
                color = "#FF0000",
                createdAt = "2026-01-01T00:00:00Z"
            )
            database.categoryDao().insert(category)
            val income = TransactionEntity(
                id = "inc-1",
                type = "INCOME",
                accountId = "acc-1",
                amount = "500.00",
                currency = "COP",
                date = "2026-08-01",
                categoryId = "cat-1",
                description = "",
                createdAt = "2026-08-01T10:00:00Z"
            )
            database.transactionDao().insert(income)
            val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
            val result = repository.getAll(criteria).first()
            assertTrue(result is Outcome.Success)
            val accounts = (result as Outcome.Success).value
            assertEquals(1, accounts.size)
            assertEquals(1, accounts.first().incomes.size)
        }

    @Test
    fun `given empty database, when getAll, then returns empty list`() = runTest {
        val result = repository.getAll().first()
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.isEmpty())
    }
}
