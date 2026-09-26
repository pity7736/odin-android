package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
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
        repository = RoomTransactionRepository(database.transactionDao())
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
    fun `given no transaction with the id, when finding it, then returns NotFound`() = runTest {
        val result = repository.findById("missing").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
    }

    private suspend fun seedAccountsAndCategories() {
        this.database.accountDao().insert(AccountBuilder().id("acc-savings").name("Ahorros").build().toEntity())
        this.database.accountDao().insert(AccountBuilder().id("acc-cash").name("Efectivo").build().toEntity())
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
}
