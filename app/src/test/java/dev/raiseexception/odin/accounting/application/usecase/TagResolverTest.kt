package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.TagNameError
import dev.raiseexception.odin.accounting.domain.TagResolutionError
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.accounting.domain.repository.TagRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.TagBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagResolverTest {

    private val tagRepository = mockk<TagRepository>()
    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val tagResolver = TagResolver(tagRepository, fixedClock)

    @Test
    fun `given an existing tag id that is found, when resolving, then returns its id`() = runTest {
        val nalaTag = TagBuilder().id("tag-nala").name("Nala").build()
        coEvery { tagRepository.findById("tag-nala") } returns Outcome.Success(nalaTag)

        val result = tagResolver.resolve(listOf(TagInput.Existing("tag-nala")))

        assertEquals(Outcome.Success(listOf("tag-nala")), result)
    }

    @Test
    fun `given an existing tag id that is not found, when resolving, then fails with StorageFailure`() = runTest {
        coEvery { tagRepository.findById("tag-missing") } returns Outcome.Success(null)

        val result = tagResolver.resolve(listOf(TagInput.Existing("tag-missing")))

        assertTrue((result as Outcome.Failure).error is TagResolutionError.StorageFailure)
    }

    @Test
    fun `given a new name with no match, when resolving, then adds a tag with the trimmed name and returns its id`() =
        runTest {
            val addedTag = slot<Tag>()
            coEvery { tagRepository.findByNormalizedName("carro") } returns Outcome.Success(null)
            coEvery { tagRepository.add(capture(addedTag)) } returns Outcome.Success(Unit)

            val result = tagResolver.resolve(listOf(TagInput.New("  Carro  ")))

            assertEquals("Carro", addedTag.captured.name)
            assertEquals("carro", addedTag.captured.normalizedName)
            assertEquals(this@TagResolverTest.fixedInstant, addedTag.captured.createdAt)
            assertEquals(Outcome.Success(listOf(addedTag.captured.id)), result)
        }

    @Test
    fun `given a new name matching an existing tag ignoring case, when resolving, then reuses it without adding`() =
        runTest {
            val nalaTag = TagBuilder().id("tag-nala").name("Nala").build()
            coEvery { tagRepository.findByNormalizedName("nala") } returns Outcome.Success(nalaTag)

            val result = tagResolver.resolve(listOf(TagInput.New("nala")))

            assertEquals(Outcome.Success(listOf("tag-nala")), result)
            coVerify(exactly = 0) { tagRepository.add(any()) }
        }

    @Test
    fun `given a new name matching an existing tag ignoring accents, when resolving, then returns that tag id`() =
        runTest {
            val cafeTag = TagBuilder().id("tag-cafe").name("Café").build()
            coEvery { tagRepository.findByNormalizedName("cafe") } returns Outcome.Success(cafeTag)

            val result = tagResolver.resolve(listOf(TagInput.New("Cafe")))

            assertEquals(Outcome.Success(listOf("tag-cafe")), result)
            coVerify(exactly = 0) { tagRepository.add(any()) }
        }

    @Test
    fun `given a new name of 31 characters, when resolving, then fails with InvalidName carrying the message`() =
        runTest {
            val result = tagResolver.resolve(listOf(TagInput.New("a".repeat(Tag.MAX_NAME_LENGTH + 1))))

            val error = (result as Outcome.Failure).error
            assertTrue(error is TagResolutionError.InvalidName)
            assertTrue((error as TagResolutionError.InvalidName).nameError is TagNameError.TooLong)
            assertEquals("La etiqueta no puede superar 30 caracteres.", error.externalMessage)
            coVerify(exactly = 0) { tagRepository.add(any()) }
        }

    @Test
    fun `given a blank new name, when resolving, then it is skipped without touching the repository`() = runTest {
        val nalaTag = TagBuilder().id("tag-nala").name("Nala").build()
        coEvery { tagRepository.findById("tag-nala") } returns Outcome.Success(nalaTag)

        val result = tagResolver.resolve(listOf(TagInput.New("   "), TagInput.Existing("tag-nala")))

        assertEquals(Outcome.Success(listOf("tag-nala")), result)
        coVerify(exactly = 0) { tagRepository.findByNormalizedName(any()) }
        coVerify(exactly = 0) { tagRepository.add(any()) }
    }

    @Test
    fun `given two new names with the same normalized name, when resolving, then adds once and returns the id twice`() =
        runTest {
            val addedTag = slot<Tag>()
            coEvery { tagRepository.findByNormalizedName("cafe") } returns Outcome.Success(null)
            coEvery { tagRepository.add(capture(addedTag)) } returns Outcome.Success(Unit)

            val result = tagResolver.resolve(listOf(TagInput.New("Café"), TagInput.New("cafe")))

            coVerify(exactly = 1) { tagRepository.add(any()) }
            assertEquals(Outcome.Success(listOf(addedTag.captured.id, addedTag.captured.id)), result)
        }

    @Test
    fun `given a storage failure looking up by id, when resolving, then fails with StorageFailure`() = runTest {
        coEvery { tagRepository.findById("tag-nala") } returns Outcome.Failure(StorageError("disk error"))

        val result = tagResolver.resolve(listOf(TagInput.Existing("tag-nala")))

        val error = (result as Outcome.Failure).error
        assertTrue(error is TagResolutionError.StorageFailure)
        assertEquals("disk error", error.internalMessage)
    }

    @Test
    fun `given a storage failure looking up by name, when resolving, then fails with StorageFailure`() = runTest {
        coEvery { tagRepository.findByNormalizedName("carro") } returns Outcome.Failure(StorageError("disk error"))

        val result = tagResolver.resolve(listOf(TagInput.New("Carro")))

        assertTrue((result as Outcome.Failure).error is TagResolutionError.StorageFailure)
        coVerify(exactly = 0) { tagRepository.add(any()) }
    }

    @Test
    fun `given a storage failure adding, when resolving, then fails with StorageFailure`() = runTest {
        coEvery { tagRepository.findByNormalizedName("carro") } returns Outcome.Success(null)
        coEvery { tagRepository.add(any()) } returns Outcome.Failure(StorageError("disk full"))

        val result = tagResolver.resolve(listOf(TagInput.New("Carro")))

        val error = (result as Outcome.Failure).error
        assertTrue(error is TagResolutionError.StorageFailure)
        assertEquals("disk full", error.internalMessage)
    }

    @Test
    fun `given no tag inputs, when resolving, then returns an empty list without repository calls`() = runTest {
        val result = tagResolver.resolve(emptyList())

        assertEquals(Outcome.Success(emptyList<String>()), result)
        confirmVerified(tagRepository)
    }
}
