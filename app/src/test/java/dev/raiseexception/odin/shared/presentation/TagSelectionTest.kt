package dev.raiseexception.odin.shared.presentation

import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.testutil.TagBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagSelectionTest {

    private val nalaTag = TagBuilder().id("tag-nala").name("Nala").build()
    private val tobyTag = TagBuilder().id("tag-toby").name("Toby").build()
    private val comidaTag = TagBuilder().id("tag-comida").name("Comida").build()
    private val cafeTag = TagBuilder().id("tag-cafe").name("Café").build()
    private val monoTag = TagBuilder().id("tag-mono").name("mono").build()
    private val monoWithEnyeTag = TagBuilder().id("tag-moño").name("Moño").build()

    @Test
    fun `given mono exists, when adding Moño, then a new tag is appended instead of reusing mono`() {
        val selection = TagSelection().withText("Moño")

        val result = selection.withTextAdded(listOf(this.monoTag))

        assertEquals(listOf(SelectedTag("Moño", TagInput.New("Moño"))), result.selected)
    }

    @Test
    fun `given Moño exists, when adding moño, then the existing Moño is reused`() {
        val selection = TagSelection().withText("moño")

        val result = selection.withTextAdded(listOf(this.monoWithEnyeTag))

        assertEquals(listOf(SelectedTag("Moño", TagInput.Existing("tag-moño"))), result.selected)
    }

    @Test
    fun `given mono typed, when suggesting, then mono is suggested but not Moño`() {
        val selection = TagSelection().withText("mono")

        val result = selection.suggestions(listOf(this.monoWithEnyeTag, this.monoTag))

        assertEquals(listOf(this.monoTag), result)
    }

    @Test
    fun `given an unknown name typed, when adding the text, then a new tag is appended and the text cleared`() {
        val selection = TagSelection().withText("Carro")

        val result = selection.withTextAdded(listOf(this.nalaTag))

        assertEquals(listOf(SelectedTag("Carro", TagInput.New("Carro"))), result.selected)
        assertEquals("", result.text)
        assertNull(result.error)
    }

    @Test
    fun `given a name with surrounding spaces typed, when adding the text, then the new tag has the trimmed name`() {
        val selection = TagSelection().withText("  Nala  ")

        val result = selection.withTextAdded(emptyList())

        assertEquals(listOf(SelectedTag("Nala", TagInput.New("Nala"))), result.selected)
    }

    @Test
    fun `given an existing tag typed in another case, when adding the text, then the existing tag is added`() {
        val selection = TagSelection().withText("nala")

        val result = selection.withTextAdded(listOf(this.nalaTag))

        assertEquals(listOf(SelectedTag("Nala", TagInput.Existing("tag-nala"))), result.selected)
    }

    @Test
    fun `given an existing tag typed without accents, when adding the text, then the accented tag is added`() {
        val selection = TagSelection().withText("Cafe")

        val result = selection.withTextAdded(listOf(this.cafeTag))

        assertEquals(listOf(SelectedTag("Café", TagInput.Existing("tag-cafe"))), result.selected)
    }

    @Test
    fun `given nothing typed, when adding the text, then nothing is added and no message is shown`() {
        val selection = TagSelection()

        val result = selection.withTextAdded(listOf(this.nalaTag))

        assertEquals(selection, result)
    }

    @Test
    fun `given only spaces typed, when adding the text, then nothing is added and no message is shown`() {
        val selection = TagSelection().withText("   ")

        val result = selection.withTextAdded(listOf(this.nalaTag))

        assertTrue(result.selected.isEmpty())
        assertNull(result.error)
    }

    @Test
    fun `given a name of 31 characters typed, when adding the text, then it is not added and the error is shown`() {
        val selection = TagSelection().withText("a".repeat(Tag.MAX_NAME_LENGTH + 1))

        val result = selection.withTextAdded(emptyList())

        assertTrue(result.selected.isEmpty())
        assertEquals("La etiqueta no puede superar 30 caracteres.", result.error)
    }

    @Test
    fun `given a name of exactly 30 characters typed, when adding the text, then it is added`() {
        val name = "a".repeat(Tag.MAX_NAME_LENGTH)
        val selection = TagSelection().withText(name)

        val result = selection.withTextAdded(emptyList())

        assertEquals(listOf(SelectedTag(name, TagInput.New(name))), result.selected)
    }

    @Test
    fun `given the too long error, when typing again, then the error is cleared`() {
        val selection = TagSelection().withText("a".repeat(Tag.MAX_NAME_LENGTH + 1)).withTextAdded(emptyList())

        val result = selection.withText("Nala")

        assertNull(result.error)
    }

    @Test
    fun `given Nala already added, when adding nala typed, then Nala stays only once`() {
        val selection = TagSelection().withTagPicked(this.nalaTag).withText("nala")

        val result = selection.withTextAdded(listOf(this.nalaTag))

        assertEquals(listOf(SelectedTag("Nala", TagInput.Existing("tag-nala"))), result.selected)
    }

    @Test
    fun `given a new tag already added, when adding it again typed in another case, then it stays only once`() {
        val selection = TagSelection().withText("Carro").withTextAdded(emptyList()).withText("carro")

        val result = selection.withTextAdded(emptyList())

        assertEquals(listOf(SelectedTag("Carro", TagInput.New("Carro"))), result.selected)
    }

    @Test
    fun `given Nala and Toby added, when adding Comida, then Comida appears after them`() {
        val selection = TagSelection().withTagPicked(this.nalaTag).withTagPicked(this.tobyTag).withText("Comida")

        val result = selection.withTextAdded(listOf(this.nalaTag, this.tobyTag, this.comidaTag))

        assertEquals(listOf("Nala", "Toby", "Comida"), result.selected.map { it.name })
    }

    @Test
    fun `given four tags, when adding a fifth, then the selection is at the limit without an error`() {
        val selection = this.selectionWith(FOUR_TAGS).withText("Quinta")

        val result = selection.withTextAdded(emptyList())

        assertEquals(FIVE_TAGS, result.selected.size)
        assertTrue(result.isAtLimit)
        assertNull(result.error)
    }

    @Test
    fun `given five tags, when adding another typed tag, then it is not added and no error is shown`() {
        val selection = this.selectionWith(FIVE_TAGS).withText("Sexta")

        val result = selection.withTextAdded(emptyList())

        assertEquals(FIVE_TAGS, result.selected.size)
        assertNull(result.error)
    }

    @Test
    fun `given five tags, when picking another tag, then it is not added and no error is shown`() {
        val selection = this.selectionWith(FIVE_TAGS)

        val result = selection.withTagPicked(this.nalaTag)

        assertEquals(FIVE_TAGS, result.selected.size)
        assertNull(result.error)
    }

    @Test
    fun `given five saved tags, when the selection is built, then it is at the limit without an error`() {
        val result = this.selectionWith(FIVE_TAGS)

        assertTrue(result.isAtLimit)
        assertNull(result.error)
    }

    @Test
    fun `given five tags, when removing one, then tags are accepted again`() {
        val selection = this.selectionWith(FIVE_TAGS)

        val result = selection.withTagRemoved(0)

        assertFalse(result.isAtLimit)
        assertNull(result.error)
        assertEquals(FOUR_TAGS, result.selected.size)
    }

    @Test
    fun `given a tag picked, when picking, then it is appended as an existing tag and the text cleared`() {
        val selection = TagSelection().withTagPicked(this.nalaTag).withText("co")

        val result = selection.withTagPicked(this.comidaTag)

        assertEquals(
            listOf(
                SelectedTag("Nala", TagInput.Existing("tag-nala")),
                SelectedTag("Comida", TagInput.Existing("tag-comida"))
            ),
            result.selected
        )
        assertEquals("", result.text)
    }

    @Test
    fun `given Nalaa and Comida added, when removing Nalaa, then only Comida remains`() {
        val selection = TagSelection().withText("Nalaa").withTextAdded(emptyList()).withTagPicked(this.comidaTag)

        val result = selection.withTagRemoved(0)

        assertEquals(listOf(SelectedTag("Comida", TagInput.Existing("tag-comida"))), result.selected)
    }

    @Test
    fun `given a selection, when reading its inputs, then they follow the selection order`() {
        val selection = TagSelection().withTagPicked(this.nalaTag).withText("Carro").withTextAdded(emptyList())

        assertEquals(listOf(TagInput.Existing("tag-nala"), TagInput.New("Carro")), selection.inputs)
    }

    @Test
    fun `given tags with ñ, when the field is focused, then ñ is suggested right after n`() {
        val zapatoTag = TagBuilder().id("tag-zapato").name("Zapato").build()
        val nameTag = TagBuilder().id("tag-ñame").name("Ñame").build()

        val result = TagSelection().suggestions(listOf(zapatoTag, nameTag, this.nalaTag))

        assertEquals(listOf("Nala", "Ñame", "Zapato"), result.map { it.name })
    }

    @Test
    fun `given saved tags with ñ, when building the selection, then ñ is placed right after n`() {
        val mozoTag = TagBuilder().id("tag-mozo").name("mozo").build()

        val result = TagSelection.of(listOf(mozoTag, this.monoWithEnyeTag, this.monoTag))

        assertEquals(listOf("mono", "Moño", "mozo"), result.selected.map { it.name })
    }

    @Test
    fun `given Nala added, when the field is focused, then the other tags are suggested alphabetically`() {
        val selection = TagSelection().withTagPicked(this.nalaTag)

        val result = selection.suggestions(listOf(this.nalaTag, this.tobyTag, this.comidaTag))

        assertEquals(listOf("Comida", "Toby"), result.map { it.name })
    }

    @Test
    fun `given tags in mixed case and accents, when the field is focused, then suggestions ignore case and accents`() {
        val tags = listOf("Toby", "comida", "Álamo", "Bus").map { TagBuilder().name(it).build() }

        val result = TagSelection().suggestions(tags)

        assertEquals(listOf("Álamo", "Bus", "comida", "Toby"), result.map { it.name })
    }

    @Test
    fun `given AL typed, when suggesting, then only the tags containing it are suggested in order`() {
        val tags = listOf("Nala", "Almuerzo", "Gasolina").map { TagBuilder().name(it).build() }

        val result = TagSelection().withText("AL").suggestions(tags)

        assertEquals(listOf("Almuerzo", "Nala"), result.map { it.name })
    }

    @Test
    fun `given cafe typed, when suggesting, then Café is suggested`() {
        val result = TagSelection().withText("cafe").suggestions(listOf(this.cafeTag, this.nalaTag))

        assertEquals(listOf("Café"), result.map { it.name })
    }

    @Test
    fun `given saved tags, when building the selection from them, then they are existing tags in alphabetical order`() {
        val result = TagSelection.of(listOf(this.nalaTag, this.comidaTag))

        assertEquals(
            listOf(
                SelectedTag("Comida", TagInput.Existing("tag-comida")),
                SelectedTag("Nala", TagInput.Existing("tag-nala"))
            ),
            result.selected
        )
        assertEquals("", result.text)
        assertNull(result.error)
    }

    private fun selectionWith(count: Int): TagSelection =
        TagSelection.of((1..count).map { TagBuilder().id("tag-$it").name("Etiqueta $it").build() })

    private companion object {
        const val FOUR_TAGS = 4
        const val FIVE_TAGS = 5
    }
}
