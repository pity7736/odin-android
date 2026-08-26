package dev.raiseexception.odin.accounting.domain.model

import java.math.BigDecimal

class Money private constructor(
    val amount: BigDecimal,
    val currency: Currency
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return this.amount.compareTo(other.amount) == 0 && this.currency == other.currency
    }

    override fun hashCode(): Int {
        val amountHash = this.amount.stripTrailingZeros().hashCode()
        return HASH_MULTIPLIER * amountHash + this.currency.hashCode()
    }

    override fun toString(): String = "Money(amount=${this.amount.toPlainString()}, currency=${this.currency})"

    companion object {
        private const val MAX_DECIMAL_PLACES = 2
        private const val HASH_MULTIPLIER = 31

        fun of(amount: BigDecimal, currency: Currency): Money {
            require(amount.scale() <= MAX_DECIMAL_PLACES) {
                "Money amount must have at most $MAX_DECIMAL_PLACES decimal places"
            }
            return Money(amount, currency)
        }
    }
}
