package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.repository.TagRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.TagBuilder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TagListerTest {

    private val tagRepository = mockk<TagRepository>()
    private val tagLister = TagLister(tagRepository)

    @Test
    fun `given tags in use, when listing, then emits the repository tags`() = runTest {
        val comidaTag = TagBuilder().name("Comida").build()
        val nalaTag = TagBuilder().name("Nala").build()
        every { tagRepository.getAll() } returns flowOf(Outcome.Success(listOf(comidaTag, nalaTag)))

        val result = tagLister.list().first()

        assertEquals(Outcome.Success(listOf(comidaTag, nalaTag)), result)
    }

    @Test
    fun `given a storage failure, when listing, then propagates the failure`() = runTest {
        val failure = Outcome.Failure(StorageError("disk error"))
        every { tagRepository.getAll() } returns flowOf(failure)

        val result = tagLister.list().first()

        assertEquals(failure, result)
    }
}
