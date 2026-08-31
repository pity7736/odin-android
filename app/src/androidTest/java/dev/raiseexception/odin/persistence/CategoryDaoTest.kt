package dev.raiseexception.odin.persistence

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CategoryDaoTest {

    private lateinit var database: OdinDatabase
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OdinDatabase::class.java
        ).allowMainThreadQueries().build()
        categoryDao = database.categoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun given_no_entities_when_getAll_then_returns_empty_list() = runTest {
        val result = categoryDao.getAll()

        assertTrue(result.isEmpty())
    }

    @Test
    fun given_an_entity_inserted_when_getAll_then_returns_that_entity() = runTest {
        val entity = CategoryEntity(id = "id-1", data = byteArrayOf(1, 2, 3))
        categoryDao.insert(entity)

        val result = categoryDao.getAll()

        assertEquals(1, result.size)
        assertEquals("id-1", result.first().id)
    }

    @Test
    fun given_two_entities_inserted_when_getAll_then_returns_both() = runTest {
        categoryDao.insert(CategoryEntity(id = "id-1", data = byteArrayOf(1)))
        categoryDao.insert(CategoryEntity(id = "id-2", data = byteArrayOf(2)))

        val result = categoryDao.getAll()

        assertEquals(2, result.size)
    }

    @Test(expected = Exception::class)
    fun given_an_entity_with_duplicate_id_inserted_when_inserting_again_then_throws() = runTest {
        val entity = CategoryEntity(id = "id-1", data = byteArrayOf(1))
        categoryDao.insert(entity)
        categoryDao.insert(entity)
    }
}
