package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.accounting.domain.TagNameError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }

    @Test
    fun `given a name with spaces and accents, when normalizing, then trims lowercases and strips accents`() {
        assertEquals("cafe", Tag.normalize("  Café "))
    }

    @Test
    fun `given an uppercase accented name, when normalizing, then returns it lowercase without accents`() {
        assertEquals("alamo", Tag.normalize("ÁLAMO"))
    }

    @Test
    fun `given a name with ñ, when normalizing, then keeps ñ as its own letter`() {
        assertEquals("moño", Tag.normalize("Moño"))
    }

    @Test
    fun `given an uppercase Ñ, when normalizing, then returns it as a lowercase ñ`() {
        assertEquals("moño", Tag.normalize("MOÑO"))
    }

    @Test
    fun `given Moño and mono, when normalizing, then they are different names`() {
        assertNotEquals(Tag.normalize("mono"), Tag.normalize("Moño"))
    }

    @Test
    fun `given Zapato, Ñame and Nala, when sorting alphabetically, then Ñame comes right after Nala`() {
        val tags = listOf("Zapato", "Ñame", "Nala").map { this.tagNamed(it) }

        val result = tags.sortedWith(Tag.ALPHABETICAL_ORDER)

        assertEquals(listOf("Nala", "Ñame", "Zapato"), result.map { it.name })
    }

    @Test
    fun `given mozo, moño and mono, when sorting alphabetically, then moño is between mono and mozo`() {
        val tags = listOf("mozo", "moño", "mono").map { this.tagNamed(it) }

        val result = tags.sortedWith(Tag.ALPHABETICAL_ORDER)

        assertEquals(listOf("mono", "moño", "mozo"), result.map { it.name })
    }

    @Test
    fun `given names in mixed case and accents, when sorting alphabetically, then case and accents are ignored`() {
        val tags = listOf("Toby", "comida", "Álamo", "Bus").map { this.tagNamed(it) }

        val result = tags.sortedWith(Tag.ALPHABETICAL_ORDER)

        assertEquals(listOf("Álamo", "Bus", "comida", "Toby"), result.map { it.name })
    }

    @Test
    fun `given an empty name, when validating, then fails with Blank`() {
        val result = Tag.validateName("")

        assertTrue((result as Outcome.Failure).error is TagNameError.Blank)
    }

    @Test
    fun `given a name of only spaces, when validating, then fails with Blank`() {
        val result = Tag.validateName("   ")

        assertTrue((result as Outcome.Failure).error is TagNameError.Blank)
    }

    @Test
    fun `given a name of 31 characters, when validating, then fails with TooLong and its message`() {
        val result = Tag.validateName("a".repeat(Tag.MAX_NAME_LENGTH + 1))

        val error = (result as Outcome.Failure).error
        assertTrue(error is TagNameError.TooLong)
        assertEquals("La etiqueta no puede superar 30 caracteres.", error.externalMessage)
    }

    @Test
    fun `given a name of exactly 30 characters, when validating, then succeeds`() {
        val name = "a".repeat(Tag.MAX_NAME_LENGTH)

        val result = Tag.validateName(name)

        assertEquals(Outcome.Success(name), result)
    }

    @Test
    fun `given a name with surrounding spaces, when validating, then succeeds with the trimmed name`() {
        val result = Tag.validateName("  Nala  ")

        assertEquals(Outcome.Success("Nala"), result)
    }

    @Test
    fun `given a name of 30 characters surrounded by spaces, when validating, then succeeds`() {
        val name = "a".repeat(Tag.MAX_NAME_LENGTH)

        val result = Tag.validateName("  $name  ")

        assertEquals(Outcome.Success(name), result)
    }

    @Test
    fun `given a valid name, when creating, then returns a tag with trimmed and normalized names`() {
        val result = Tag.create("  Café  ", this.fixedClock)

        val tag = (result as Outcome.Success).value
        assertEquals("Café", tag.name)
        assertEquals("cafe", tag.normalizedName)
        assertTrue(tag.id.isNotEmpty())
        assertEquals(this.fixedInstant, tag.createdAt)
    }

    @Test
    fun `given two valid names, when creating, then each tag gets its own id`() {
        val nalaTag = (Tag.create("Nala", this.fixedClock) as Outcome.Success).value
        val tobyTag = (Tag.create("Toby", this.fixedClock) as Outcome.Success).value

        assertTrue(nalaTag.id != tobyTag.id)
    }

    @Test
    fun `given a blank name, when creating, then fails with Blank`() {
        val result = Tag.create("  ", this.fixedClock)

        assertTrue((result as Outcome.Failure).error is TagNameError.Blank)
    }

    @Test
    fun `given a name of 31 characters, when creating, then fails with TooLong`() {
        val result = Tag.create("a".repeat(Tag.MAX_NAME_LENGTH + 1), this.fixedClock)

        assertTrue((result as Outcome.Failure).error is TagNameError.TooLong)
    }

    @Test
    fun `given stored values, when restoring, then the tag keeps them exactly`() {
        val tag = Tag.restore("tag-1", "Café", "cafe", this.fixedInstant)

        assertEquals("tag-1", tag.id)
        assertEquals("Café", tag.name)
        assertEquals("cafe", tag.normalizedName)
        assertEquals(this.fixedInstant, tag.createdAt)
    }

    private fun tagNamed(name: String): Tag = (Tag.create(name, this.fixedClock) as Outcome.Success).value
}
