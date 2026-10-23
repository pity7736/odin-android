package dev.raiseexception.odin.accounting.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class GmfTest {

    @Test
    fun `given an amount of 100000, when computing its tax, then 400`() {
        assertEquals(BigDecimal("400.00"), gmfTaxOf(BigDecimal("100000")))
    }

    @Test
    fun `given an amount of 12345,67, when computing its tax, then it is rounded to the cent as 49,38`() {
        assertEquals(BigDecimal("49.38"), gmfTaxOf(BigDecimal("12345.67")))
    }

    @Test
    fun `given an amount of 13,75, when computing its tax, then a half cent rounds up to 0,06`() {
        assertEquals(BigDecimal("0.06"), gmfTaxOf(BigDecimal("13.75")))
    }

    @Test
    fun `given an amount of 1, when computing its tax, then it rounds to zero`() {
        assertEquals(BigDecimal("0.00"), gmfTaxOf(BigDecimal("1.00")))
    }

    @Test
    fun `given an amount of 1,25, when computing its tax, then 0,01`() {
        assertEquals(BigDecimal("0.01"), gmfTaxOf(BigDecimal("1.25")))
    }

    @Test
    fun `given an exempt treatment, when computing the tax on 100000, then zero`() {
        assertEquals(0, GmfTreatment.Exempt.taxOn(BigDecimal("100000")).signum())
    }

    @Test
    fun `given a taxed treatment, when computing the tax on 100000, then 400`() {
        val treatment = GmfTreatment.Taxed(categoryId = "cat-impuestos", tagNames = listOf("4x1000"))

        assertEquals(BigDecimal("400.00"), treatment.taxOn(BigDecimal("100000")))
    }

    @Test
    fun `given a taxed treatment, when used for a savings account in COP, then it stays taxed`() {
        val taxed = GmfTreatment.Taxed("cat-impuestos", listOf("4x1000"))
        assertEquals(taxed, taxed.forAccount(MoneyAccountKind.SAVINGS, Currency.COP))
    }

    @Test
    fun `given a taxed treatment, when used for a cash account, then it becomes exempt`() {
        val taxed = GmfTreatment.Taxed("cat-impuestos", listOf("4x1000"))
        assertEquals(GmfTreatment.Exempt, taxed.forAccount(MoneyAccountKind.CASH, Currency.COP))
    }

    @Test
    fun `given a taxed treatment, when used for a savings account in USD, then it becomes exempt`() {
        val taxed = GmfTreatment.Taxed("cat-impuestos", listOf("4x1000"))
        assertEquals(GmfTreatment.Exempt, taxed.forAccount(MoneyAccountKind.SAVINGS, Currency.USD))
    }

    @Test
    fun `given an exempt treatment, when used for a savings account in COP, then it stays exempt`() {
        assertEquals(GmfTreatment.Exempt, GmfTreatment.Exempt.forAccount(MoneyAccountKind.SAVINGS, Currency.COP))
    }

    @Test
    fun `given an exempt treatment, when checking its category and tags, then no errors`() {
        assertNull(GmfTreatment.Exempt.categoryError())
        assertNull(GmfTreatment.Exempt.tagsError())
    }

    @Test
    fun `given a taxed treatment without category, when checking its category, then it is required`() {
        assertEquals(
            "La categoría del 4x1000 es obligatoria.",
            GmfTreatment.Taxed("", emptyList()).categoryError()
        )
    }

    @Test
    fun `given a taxed treatment with a category, when checking its category, then no error`() {
        assertNull(GmfTreatment.Taxed("cat-impuestos", emptyList()).categoryError())
    }

    @Test
    fun `given a tax tag of 31 characters, when checking the tags, then the tag length error`() {
        val taxed = GmfTreatment.Taxed("cat-impuestos", listOf("a".repeat(Tag.MAX_NAME_LENGTH + 1)))
        assertEquals("La etiqueta no puede superar 30 caracteres.", taxed.tagsError())
    }

    @Test
    fun `given six tax tags, when checking the tags, then the tag limit error`() {
        val taxed = GmfTreatment.Taxed("cat-impuestos", listOf("a", "b", "c", "d", "e", "f"))
        assertEquals(Expense.TAG_LIMIT_MESSAGE, taxed.tagsError())
    }

    @Test
    fun `given Banco and banco among the tax tags, when checking the tags, then they count as one`() {
        val taxed = GmfTreatment.Taxed("cat-impuestos", listOf("a", "b", "c", "d", "Banco", "banco"))
        assertNull(taxed.tagsError())
    }

    @Test
    fun `given a blank tax tag, when checking the tags, then it is ignored`() {
        assertNull(GmfTreatment.Taxed("cat-impuestos", listOf("4x1000", " ")).tagsError())
    }
}
