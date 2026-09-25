package dev.raiseexception.odin.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OdinDatabase::class.java
    )

    @Test
    fun given_a_v1_accounts_row_when_migrating_to_v2_then_row_survives_with_new_nullable_columns() {
        val database = helper.createDatabase(TEST_DB, 1)
        database.execSQL(
            "INSERT INTO accounts " +
                "(id, name, initialBalanceAmount, currency, type, description, createdAt) " +
                "VALUES ('acc-1', 'Ahorros', '1000.00', 'COP', 'SAVINGS', 'Fondo', '2026-01-01T00:00:00Z')"
        )
        database.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val cursor = migrated.query(
            "SELECT id, name, initialBalanceAmount, creditLimitAmount, debtAmount FROM accounts WHERE id = 'acc-1'"
        )
        assertTrue(cursor.moveToFirst())
        assertEquals("acc-1", cursor.getString(0))
        assertEquals("Ahorros", cursor.getString(1))
        assertEquals("1000.00", cursor.getString(2))
        assertTrue(cursor.isNull(3))
        assertTrue(cursor.isNull(4))
        cursor.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
