package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.shared.domain.Outcome
import java.math.BigDecimal
import java.math.RoundingMode

private val GMF_RATE = BigDecimal("0.004")
private const val GMF_DECIMAL_PLACES = 2

fun gmfTaxOf(amount: BigDecimal): BigDecimal =
    amount.multiply(GMF_RATE).setScale(GMF_DECIMAL_PLACES, RoundingMode.HALF_UP)

sealed interface GmfTreatment {
    fun taxOn(amount: BigDecimal): BigDecimal

    fun forAccount(kind: MoneyAccountKind?, currency: Currency?): GmfTreatment

    fun categoryError(): String?

    fun tagsError(): String?

    data object Exempt : GmfTreatment {
        override fun taxOn(amount: BigDecimal): BigDecimal = BigDecimal.ZERO

        override fun forAccount(kind: MoneyAccountKind?, currency: Currency?): GmfTreatment = this

        override fun categoryError(): String? = null

        override fun tagsError(): String? = null
    }

    data class Taxed(val categoryId: String, val tagNames: List<String>) : GmfTreatment {
        override fun taxOn(amount: BigDecimal): BigDecimal = gmfTaxOf(amount)

        override fun forAccount(kind: MoneyAccountKind?, currency: Currency?): GmfTreatment =
            if (kind == MoneyAccountKind.SAVINGS && currency == Currency.COP) this else Exempt

        override fun categoryError(): String? =
            if (this.categoryId.isBlank()) "La categoría del 4x1000 es obligatoria." else null

        override fun tagsError(): String? {
            val namedTags = this.tagNames.filter { it.isNotBlank() }
            val invalidName = namedTags.firstNotNullOfOrNull { name ->
                (Tag.validateName(name) as? Outcome.Failure)?.error?.externalMessage
            }
            return when {
                invalidName != null -> invalidName
                namedTags.distinctBy { Tag.normalize(it) }.size > Expense.MAX_TAGS -> Expense.TAG_LIMIT_MESSAGE
                else -> null
            }
        }
    }
}
