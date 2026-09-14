package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.CategoryBuilder
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

@RunWith(RobolectricTestRunner::class)
class RoomCategoryRepositoryTest {

    private lateinit var database: OdinDatabase
    private lateinit var repository: RoomCategoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCategoryRepository(database.categoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given a category, when added and read back, then all fields match`() = runTest {
        val category = CategoryBuilder()
            .name("Alimentación")
            .type(CategoryType.EXPENSE)
            .description("Comida y mercado")
            .color("#E57373")
            .createdAt(Instant.parse("2026-08-01T10:00:00Z"))
            .build()
        repository.add(category)
        val result = repository.getAll().first()
        assertTrue(result is Outcome.Success)
        val categories = (result as Outcome.Success).value
        assertEquals(1, categories.size)
        val restored = categories.first()
        assertEquals(category.id, restored.id)
        assertEquals("Alimentación", restored.name)
        assertEquals(CategoryType.EXPENSE, restored.type)
        assertEquals("Comida y mercado", restored.description)
        assertEquals("#E57373", restored.color)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), restored.createdAt)
    }

    @Test
    fun `given existing category, when checking existsByNameAndType with same name and type, then returns true`() =
        runTest {
            repository.add(CategoryBuilder().name("Alimentación").type(CategoryType.EXPENSE).build())
            val result = repository.existsByNameAndType("Alimentación", CategoryType.EXPENSE)
            assertTrue(result is Outcome.Success)
            assertTrue((result as Outcome.Success).value)
        }

    @Test
    fun `given existing category, when checking existsByNameAndType case-insensitive, then returns true`() = runTest {
        repository.add(CategoryBuilder().name("Alimentación").type(CategoryType.EXPENSE).build())
        val result = repository.existsByNameAndType("alimentación", CategoryType.EXPENSE)
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given existing category, when checking different name, then returns false`() = runTest {
        repository.add(CategoryBuilder().name("Alimentación").type(CategoryType.EXPENSE).build())
        val result = repository.existsByNameAndType("Transporte", CategoryType.EXPENSE)
        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given multiple categories, when getAll, then returns all`() = runTest {
        repository.add(CategoryBuilder().name("Alimentación").type(CategoryType.EXPENSE).build())
        repository.add(CategoryBuilder().name("Salario").type(CategoryType.INCOME).build())
        val result = repository.getAll().first()
        assertTrue(result is Outcome.Success)
        assertEquals(2, (result as Outcome.Success).value.size)
    }

    @Test
    fun `given empty database, when getAll, then returns empty list`() = runTest {
        val result = repository.getAll().first()
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.isEmpty())
    }

    @Test
    fun `given an existing category, when finding by id, then returns it`() = runTest {
        val category = CategoryBuilder()
            .id("cat-001")
            .name("Transporte")
            .type(CategoryType.EXPENSE)
            .description("Buses y taxis")
            .color("#42A5F5")
            .createdAt(Instant.parse("2026-09-01T12:00:00Z"))
            .build()
        repository.add(category)
        val result = repository.findById("cat-001").first()
        assertTrue(result is Outcome.Success)
        val restored = (result as Outcome.Success).value
        assertEquals("cat-001", restored.id)
        assertEquals("Transporte", restored.name)
        assertEquals(CategoryType.EXPENSE, restored.type)
        assertEquals("Buses y taxis", restored.description)
        assertEquals("#42A5F5", restored.color)
        assertEquals(Instant.parse("2026-09-01T12:00:00Z"), restored.createdAt)
    }

    @Test
    fun `given no matching category, when finding by id, then returns not found`() = runTest {
        val result = repository.findById("nonexistent").first()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CategoryLookupError.NotFound)
    }
}
