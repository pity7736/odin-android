package dev.raiseexception.odin.reporting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money

data class SpendingReport(
    val period: ReportPeriod,
    val currencies: List<Currency>,
    val currency: Currency,
    val total: Money,
    val categories: List<CategorySpending>,
)
