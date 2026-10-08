package dev.raiseexception.odin.reporting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Money
import java.math.BigDecimal

sealed interface DisplayedShare {
    data class Whole(val percent: Int) : DisplayedShare
    data object BelowOnePercent : DisplayedShare
}

data class CategorySpending(
    val categoryId: String,
    val name: String,
    val color: String,
    val total: Money,
    val share: BigDecimal,
    val displayedShare: DisplayedShare,
)
