package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.coEvery
import io.mockk.mockk
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
class RoomIncomeRepositoryTest {

    private lateinit var database: OdinDatabase
    private lateinit var repository: RoomIncomeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomIncomeRepository(database.transactionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given an income, when added and read back via DAO, then stored as INCOME type with correct data`() = runTest {
        val account = AccountBuilder().id("acc-1").build()
        database.accountDao().insert(account.toEntity())
        val category = CategoryEntity(
            id = "cat-1",
            name = "Salario",
            type = "INCOME",
            description = "",
            color = "#FF0000",
            createdAt = "2026-01-01T00:00:00Z"
        )
        database.categoryDao().insert(category)
        val income = Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("2000.00"), Currency.COP),
            date = LocalDate.parse("2026-08-01"),
            categoryId = "cat-1",
            description = "Pago mensual",
            createdAt = Instant.parse("2026-08-01T10:00:00Z")
        )
        val result = repository.add(income)
        assertTrue(result is Outcome.Success)
        val accountWithTransactions = database.accountDao().findByIdWithTransactions("acc-1").first()!!
        assertEquals(1, accountWithTransactions.transactions.size)
        val stored = accountWithTransactions.transactions.first().transaction
        assertEquals("INCOME", stored.type)
        assertEquals("inc-1", stored.id)
        assertEquals("2000.00", stored.amount)
    }

    @Test
    fun `given a stored income, when updating, then the row has the new values keeping id account type createdAt`() =
        runTest {
            database.accountDao().insert(AccountBuilder().id("acc-1").build().toEntity())
            database.categoryDao().insert(incomeCategory("cat-1", "Salario"))
            database.categoryDao().insert(incomeCategory("cat-2", "Bonos"))
            repository.add(income(amount = "2000.00", date = "2026-08-01", categoryId = "cat-1", description = "Pago"))
            val edited = income(amount = "2500.50", date = "2026-08-03", categoryId = "cat-2", description = "Bono")
            val result = repository.update(edited)
            assertTrue(result is Outcome.Success)
            val accountWithTransactions = database.accountDao().findByIdWithTransactions("acc-1").first()!!
            val stored = accountWithTransactions.transactions.single().transaction
            assertEquals("inc-1", stored.id)
            assertEquals("acc-1", stored.accountId)
            assertEquals("INCOME", stored.type)
            assertEquals("2026-08-01T10:00:00Z", stored.createdAt)
            assertEquals("2500.50", stored.amount)
            assertEquals("2026-08-03", stored.date)
            assertEquals("cat-2", stored.categoryId)
            assertEquals("Bono", stored.description)
        }

    @Test
    fun `given the storage fails, when updating an income, then fails with a storage error`() = runTest {
        val failingTransactionDao = mockk<TransactionDao>()
        coEvery { failingTransactionDao.update(any()) } throws SQLiteException("disk I/O error")
        val failingRepository = RoomIncomeRepository(failingTransactionDao)
        val result = failingRepository.update(
            income(amount = "2000.00", date = "2026-08-01", categoryId = "cat-1", description = "")
        )
        assertTrue((result as Outcome.Failure).error is StorageError)
        assertEquals("disk I/O error", result.error.internalMessage)
    }

    private fun income(amount: String, date: String, categoryId: String, description: String): Income =
        Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal(amount), Currency.COP),
            date = LocalDate.parse(date),
            categoryId = categoryId,
            description = description,
            createdAt = Instant.parse("2026-08-01T10:00:00Z")
        )

    private fun incomeCategory(id: String, name: String) = CategoryEntity(
        id = id,
        name = name,
        type = "INCOME",
        description = "",
        color = "#FF0000",
        createdAt = "2026-01-01T00:00:00Z"
    )
}
