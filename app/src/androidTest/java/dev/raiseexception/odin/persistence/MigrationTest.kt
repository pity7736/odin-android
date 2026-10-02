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

    @Test
    fun given_v2_expenses_when_migrating_to_v3_then_they_survive_with_tag_tables() {
        val database = helper.createDatabase(TEST_DB, 2)
        database.execSQL(
            "INSERT INTO accounts " +
                "(id, name, initialBalanceAmount, currency, type, description, createdAt) " +
                "VALUES ('acc-1', 'Ahorros', '1000.00', 'COP', 'SAVINGS', '', '2026-01-01T00:00:00Z')"
        )
        database.execSQL(
            "INSERT INTO categories (id, name, type, description, color, isSystem, createdAt) " +
                "VALUES ('cat-1', 'Perros', 'EXPENSE', '', '#FF0000', 0, '2026-01-01T00:00:00Z')"
        )
        database.execSQL(
            "INSERT INTO transactions " +
                "(id, type, accountId, amount, currency, date, categoryId, description, createdAt) " +
                "VALUES ('exp-1', 'EXPENSE', 'acc-1', '500.00', 'COP', '2026-01-02', 'cat-1', 'Comida', " +
                "'2026-01-02T00:00:00Z')"
        )
        database.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        val expenseCursor = migrated.query("SELECT id, amount, description FROM transactions WHERE id = 'exp-1'")
        assertTrue(expenseCursor.moveToFirst())
        assertEquals("exp-1", expenseCursor.getString(0))
        assertEquals("500.00", expenseCursor.getString(1))
        assertEquals("Comida", expenseCursor.getString(2))
        expenseCursor.close()
        val tagCursor = migrated.query("SELECT COUNT(*) FROM tags")
        assertTrue(tagCursor.moveToFirst())
        assertEquals(0, tagCursor.getInt(0))
        tagCursor.close()
        val linkCursor = migrated.query("SELECT COUNT(*) FROM expense_tags")
        assertTrue(linkCursor.moveToFirst())
        assertEquals(0, linkCursor.getInt(0))
        linkCursor.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
