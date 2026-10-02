package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.persistence.OdinDatabase
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.TagBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTagRepositoryTest {

    private lateinit var database: OdinDatabase
    private lateinit var repository: RoomTagRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OdinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomTagRepository(database.tagDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `given a tag, when added and found by id, then all fields match`() = runTest {
        val cafeTag = TagBuilder().id("tag-cafe").name("Café").createdAt(Instant.parse("2026-08-01T10:00:00Z")).build()
        repository.add(cafeTag)
        val restored = (repository.findById("tag-cafe") as Outcome.Success).value!!
        assertEquals("tag-cafe", restored.id)
        assertEquals("Café", restored.name)
        assertEquals("cafe", restored.normalizedName)
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), restored.createdAt)
    }

    @Test
    fun `given no tag with the id, when finding by id, then returns null`() = runTest {
        assertEquals(Outcome.Success(null), repository.findById("missing"))
    }

    @Test
    fun `given a tag, when finding by its normalized name, then returns it`() = runTest {
        repository.add(TagBuilder().id("tag-cafe").name("Café").build())
        val found = (repository.findByNormalizedName("cafe") as Outcome.Success).value!!
        assertEquals("tag-cafe", found.id)
    }

    @Test
    fun `given no tag with the normalized name, when finding by it, then returns null`() = runTest {
        repository.add(TagBuilder().name("Café").build())
        assertEquals(Outcome.Success(null), repository.findByNormalizedName("nala"))
    }

    @Test
    fun `given tags in mixed case and accents, when getting all, then they are sorted alphabetically`() = runTest {
        repository.add(TagBuilder().name("Toby").build())
        repository.add(TagBuilder().name("comida").build())
        repository.add(TagBuilder().name("Álamo").build())
        repository.add(TagBuilder().name("Bus").build())
        val tags = (repository.getAll().first() as Outcome.Success).value
        assertEquals(listOf("Álamo", "Bus", "comida", "Toby"), tags.map { it.name })
    }

    @Test
    fun `given tags with ñ, when getting all, then ñ is sorted right after n`() = runTest {
        repository.add(TagBuilder().name("Zapato").build())
        repository.add(TagBuilder().name("Ñame").build())
        repository.add(TagBuilder().name("Nala").build())
        val tags = (repository.getAll().first() as Outcome.Success).value
        assertEquals(listOf("Nala", "Ñame", "Zapato"), tags.map { it.name })
    }

    @Test
    fun `given used and unused tags, when deleting unused, then only the tags with no expense are removed`() =
        runTest {
            seedExpense("exp-1")
            repository.add(TagBuilder().id("tag-nala").name("Nala").build())
            repository.add(TagBuilder().id("tag-nalaa").name("Nalaa").build())
            database.expenseTagDao().insertAll(listOf(ExpenseTagEntity(expenseId = "exp-1", tagId = "tag-nala")))
            val result = repository.deleteUnused()
            assertEquals(Outcome.Success(Unit), result)
            val tags = (repository.getAll().first() as Outcome.Success).value
            assertEquals(listOf("tag-nala"), tags.map { it.id })
        }

    @Test
    fun `given an existing normalized name, when adding a tag with the same normalized name, then fails`() = runTest {
        repository.add(TagBuilder().id("tag-cafe").name("Café").build())
        val result = repository.add(TagBuilder().id("tag-cafe-2").name("cafe").build())
        assertTrue(result is Outcome.Failure)
    }

    private suspend fun seedExpense(expenseId: String) {
        database.accountDao().insert(AccountBuilder().id("acc-1").build().toEntity())
        database.categoryDao().insert(
            CategoryEntity(
                id = "cat-1",
                name = "Perros",
                type = "EXPENSE",
                description = "",
                color = "#FF0000",
                createdAt = "2026-01-01T00:00:00Z"
            )
        )
        database.transactionDao().insert(
            TransactionEntity(
                id = expenseId,
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
