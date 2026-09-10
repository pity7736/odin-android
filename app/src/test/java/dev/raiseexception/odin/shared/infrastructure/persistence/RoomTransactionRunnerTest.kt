package dev.raiseexception.odin.shared.infrastructure.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.infrastructure.repository.AccountEntity
import dev.raiseexception.odin.persistence.OdinDatabase
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
class RoomTransactionRunnerTest {

    private lateinit var database: OdinDatabase
    private lateinit var transactionRunner: RoomTransactionRunner

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionRunner = RoomTransactionRunner(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given two inserts in a transaction, when block succeeds, then both are committed`() = runTest {
        val firstAccount = buildAccountEntity("acc-1", "Ahorros")
        val secondAccount = buildAccountEntity("acc-2", "Efectivo")
        transactionRunner.run {
            database.accountDao().insert(firstAccount)
            database.accountDao().insert(secondAccount)
        }
        val accounts = database.accountDao().getAll().first()
        assertEquals(2, accounts.size)
    }

    @Test
    fun `given two inserts in a transaction, when block throws, then neither is committed`() = runTest {
        val firstAccount = buildAccountEntity("acc-1", "Ahorros")
        val secondAccount = buildAccountEntity("acc-2", "Efectivo")
        val result = runCatching {
            transactionRunner.run {
                database.accountDao().insert(firstAccount)
                database.accountDao().insert(secondAccount)
                error("Simulated failure")
            }
        }
        assertTrue(result.isFailure)
        val accounts = database.accountDao().getAll().first()
        assertTrue(accounts.isEmpty())
    }

    private fun buildAccountEntity(id: String, name: String): AccountEntity =
        AccountEntity(
            id = id,
            name = name,
            initialBalanceAmount = "1000.00",
            currency = "COP",
            type = "SAVINGS",
            description = "",
            createdAt = "2026-01-01T00:00:00Z"
        )
}
