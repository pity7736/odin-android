package dev.raiseexception.odin.shared.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpanishAlphabeticalOrderTest {

    @Test
    fun `given the same word in different case, when comparing, then they are equal`() {
        assertEquals(0, SpanishAlphabeticalOrder.COMPARATOR.compare("comida", "Comida"))
    }

    @Test
    fun `given the same word with and without accents, when comparing, then they are equal`() {
        assertEquals(0, SpanishAlphabeticalOrder.COMPARATOR.compare("Café", "cafe"))
    }

    @Test
    fun `given an accented first letter, when sorting, then the accent is ignored`() {
        val result = listOf("Bus", "Álamo").sortedWith(SpanishAlphabeticalOrder.COMPARATOR)
        assertEquals(listOf("Álamo", "Bus"), result)
    }

    @Test
    fun `given mozo, Moño and mono, when sorting, then Moño is between mono and mozo`() {
        val result = listOf("mozo", "Moño", "mono").sortedWith(SpanishAlphabeticalOrder.COMPARATOR)
        assertEquals(listOf("mono", "Moño", "mozo"), result)
    }

    @Test
    fun `given Zapato, Ñame and Nala, when sorting, then Ñame comes right after Nala`() {
        val result = listOf("Zapato", "Ñame", "Nala").sortedWith(SpanishAlphabeticalOrder.COMPARATOR)
        assertEquals(listOf("Nala", "Ñame", "Zapato"), result)
    }

    @Test
    fun `given a text with spaces and accents, when normalizing, then trims lowercases and strips accents`() {
        assertEquals("cafe alamo", SpanishAlphabeticalOrder.normalize("  Café ÁLAMO "))
    }

    @Test
    fun `given a text with ñ, when normalizing, then keeps ñ as its own letter`() {
        assertEquals("moño", SpanishAlphabeticalOrder.normalize("MOÑO"))
    }
}
