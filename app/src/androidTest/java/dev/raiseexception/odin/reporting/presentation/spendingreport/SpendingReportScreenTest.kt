package dev.raiseexception.odin.reporting.presentation.spendingreport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.reporting.application.usecase.CategorySpending
import dev.raiseexception.odin.reporting.application.usecase.DisplayedShare
import dev.raiseexception.odin.reporting.application.usecase.ReportPeriod
import dev.raiseexception.odin.reporting.application.usecase.SpendingReport
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class SpendingReportScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val october = ReportPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07"))
    private val rentSpending = CategorySpending(
        categoryId = "cat-rent",
        name = "Arriendo",
        color = "#3B82F6",
        total = pesos("1000000.00"),
        share = BigDecimal("0.4200000000"),
        displayedShare = DisplayedShare.Whole(42),
    )
    private val sweetsSpending = CategorySpending(
        categoryId = "cat-sweets",
        name = "dulces",
        color = "#F97316",
        total = pesos("3000.00"),
        share = BigDecimal("0.0029910269"),
        displayedShare = DisplayedShare.BelowOnePercent,
    )
    private val octoberReport = SpendingReport(
        period = october,
        currencies = listOf(Currency.COP, Currency.USD),
        currency = Currency.COP,
        total = pesos("1003000.00"),
        categories = listOf(rentSpending, sweetsSpending),
    )

    @Test
    fun given_loading_state_when_displayed_then_shows_progress() {
        this.show(SpendingReportUiState.Loading)
        composeTestRule.onNodeWithTag("report_loading").assertIsDisplayed()
    }

    @Test
    fun given_content_state_when_displayed_then_shows_title_period_total_and_chart() {
        this.show(SpendingReportUiState.Content(this.octoberReport))
        composeTestRule.onNodeWithText("Gastos por categoría").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_range_field").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 oct 2026 – 7 oct 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("TOTAL GASTADO").assertIsDisplayed()
        composeTestRule.onNodeWithTag("total_spent").assertTextEquals("$1.003.000,00")
        composeTestRule.onNodeWithTag("donut_chart").assertIsDisplayed()
    }

    @Test
    fun given_a_category_stored_in_lowercase_when_displayed_then_shows_its_name_with_the_first_letter_capitalized() {
        this.show(SpendingReportUiState.Content(this.octoberReport))
        composeTestRule.onNodeWithText("Dulces").assertIsDisplayed()
        composeTestRule.onNodeWithText("dulces").assertDoesNotExist()
    }

    @Test
    fun given_content_state_when_displayed_then_shows_one_line_per_category_with_amount_and_share() {
        this.show(SpendingReportUiState.Content(this.octoberReport))
        composeTestRule.onNodeWithTag("category_line_cat-rent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Arriendo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_color_cat-rent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_total_cat-rent").assertTextEquals("$1.000.000,00")
        composeTestRule.onNodeWithTag("category_share_cat-rent").assertTextEquals("42%")
        composeTestRule.onNodeWithText("Dulces").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_total_cat-sweets").assertTextEquals("$3.000,00")
        composeTestRule.onNodeWithTag("category_share_cat-sweets").assertTextEquals("<1%")
    }

    @Test
    fun given_two_currencies_when_displayed_then_shows_the_chips_with_the_chosen_one_selected() {
        this.show(SpendingReportUiState.Content(this.octoberReport))
        composeTestRule.onNodeWithTag("currency_chip_COP").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithTag("currency_chip_USD").assertIsDisplayed()
    }

    @Test
    fun given_a_single_currency_when_displayed_then_hides_the_chips() {
        this.show(SpendingReportUiState.Content(this.octoberReport.copy(currencies = listOf(Currency.COP))))
        composeTestRule.onNodeWithTag("currency_chip_COP").assertDoesNotExist()
    }

    @Test
    fun given_two_currencies_when_a_chip_is_clicked_then_calls_on_currency_selected() {
        var selectedCurrency: Currency? = null
        this.show(SpendingReportUiState.Content(this.octoberReport), onCurrencySelected = { selectedCurrency = it })
        composeTestRule.onNodeWithTag("currency_chip_USD").performClick()
        assertEquals(Currency.USD, selectedCurrency)
    }

    @Test
    fun given_empty_state_when_displayed_then_shows_the_messages_and_no_total_chart_or_lines() {
        this.show(SpendingReportUiState.Empty(this.october, listOf(Currency.COP, Currency.USD), Currency.USD))
        composeTestRule.onNodeWithText("Sin gastos en USD en estas fechas.").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Cambia el rango de fechas para ver tus gastos por categoría.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("total_spent").assertDoesNotExist()
        composeTestRule.onNodeWithTag("donut_chart").assertDoesNotExist()
        composeTestRule.onNodeWithTag("category_line_cat-rent").assertDoesNotExist()
    }

    @Test
    fun given_error_state_when_displayed_then_shows_the_message_and_no_total_chart_or_lines() {
        this.show(SpendingReportUiState.Error("Error al cargar el reporte"))
        composeTestRule.onNodeWithText("Error al cargar el reporte").assertIsDisplayed()
        composeTestRule.onNodeWithTag("total_spent").assertDoesNotExist()
        composeTestRule.onNodeWithTag("donut_chart").assertDoesNotExist()
        composeTestRule.onNodeWithTag("category_line_cat-rent").assertDoesNotExist()
    }

    @Test
    fun given_the_report_when_displayed_then_reports_is_the_selected_tab() {
        this.show(SpendingReportUiState.Content(this.octoberReport))
        composeTestRule.onNodeWithTag("nav_reports").assertIsSelected()
    }

    private fun show(uiState: SpendingReportUiState, onCurrencySelected: (Currency) -> Unit = {}) {
        composeTestRule.setContent {
            SpendingReportScreen(
                uiState = uiState,
                onPeriodSelected = { _, _ -> },
                onCurrencySelected = onCurrencySelected,
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
            )
        }
    }

    private fun pesos(amount: String) = Money.of(BigDecimal(amount), Currency.COP)
}
