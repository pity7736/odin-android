package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseSchemaTest {

    private lateinit var database: OdinDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given database created, when querying table names, then all tables exist`() {
        val query = "SELECT name FROM sqlite_master " +
            "WHERE type='table' " +
            "AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT LIKE 'room_%' " +
            "ORDER BY name"
        val cursor = database.openHelper.readableDatabase.query(query)
        val tableNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()
        assertTrue(tableNames.contains("users"))
        assertTrue(tableNames.contains("accounts"))
        assertTrue(tableNames.contains("categories"))
        assertTrue(tableNames.contains("transactions"))
        assertTrue(tableNames.contains("tags"))
        assertTrue(tableNames.contains("expense_tags"))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `given a tag, when inserting another tag with the same normalized name, then throws`() = runTest {
        database.tagDao().insert(TagEntity("tag-1", "Café", "cafe", "2026-01-01T00:00:00Z"))
        database.tagDao().insert(TagEntity("tag-2", "cafe", "cafe", "2026-01-01T00:00:00Z"))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `given an expense, when linking it to an unknown tag, then throws`() = runTest {
        seedExpense()
        database.expenseTagDao().insertAll(listOf(ExpenseTagEntity(expenseId = "txn-1", tagId = "missing")))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `given a tag, when linking it to an unknown expense, then throws`() = runTest {
        database.tagDao().insert(TagEntity("tag-1", "Nala", "nala", "2026-01-01T00:00:00Z"))
        database.expenseTagDao().insertAll(listOf(ExpenseTagEntity(expenseId = "missing", tagId = "tag-1")))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `given no account exists, when inserting transaction with non-existent accountId, then throws`() = runTest {
        val categoryEntity = CategoryEntity(
            id = "cat-1",
            name = "Test",
            type = "EXPENSE",
            description = "",
            color = "#FF0000",
            createdAt = "2026-01-01T00:00:00Z"
        )
        database.categoryDao().insert(categoryEntity)
        val transactionEntity = TransactionEntity(
            id = "txn-1",
            type = "EXPENSE",
            accountId = "non-existent",
            amount = "100.00",
            currency = "COP",
            date = "2026-01-01",
            categoryId = "cat-1",
            description = "",
            createdAt = "2026-01-01T00:00:00Z"
        )
        database.transactionDao().insert(transactionEntity)
    }

    private suspend fun seedExpense() {
        database.accountDao().insert(AccountBuilder().id("acc-1").build().toEntity())
        database.categoryDao().insert(
            CategoryEntity(
                id = "cat-1",
                name = "Test",
                type = "EXPENSE",
                description = "",
                color = "#FF0000",
                createdAt = "2026-01-01T00:00:00Z"
            )
        )
        database.transactionDao().insert(
            TransactionEntity(
                id = "txn-1",
                type = "EXPENSE",
                accountId = "acc-1",
                amount = "100.00",
                currency = "COP",
                date = "2026-01-01",
                categoryId = "cat-1",
                description = "",
                createdAt = "2026-01-01T00:00:00Z"
            )
        )
    }
}
