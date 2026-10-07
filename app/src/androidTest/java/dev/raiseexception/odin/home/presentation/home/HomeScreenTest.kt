package dev.raiseexception.odin.home.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.home.application.usecase.HomeAccountEntry
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val savingsEntry = HomeAccountEntry.MoneyAccountEntry(
        id = "acc-1",
        name = "Ahorros",
        type = AccountType.SAVINGS,
        balance = pesos("150000.00")
    )

    private val visaEntry = HomeAccountEntry.CreditCardEntry(
        id = "card-1",
        name = "Visa",
        debt = pesos("500000.00"),
        availableCredit = pesos("2500000.00")
    )

    private val content = HomeUiState.Content(
        balanceTotals = listOf(pesos("5000000.00")),
        debtTotals = listOf(pesos("800000.00")),
        accounts = listOf(this.savingsEntry),
        hasMoreAccounts = false,
        recentTransactions = emptyList(),
        canTransfer = false,
        canRecordIncome = true
    )

    @Test
    fun given_balance_and_debt_totals_when_displayed_then_shows_the_balance_and_the_debt_labeled_deuda() {
        this.setScreen(this.content)
        this.composeTestRule.onNodeWithText("SALDO TOTAL").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("total_balance_COP").assertTextEquals("$5.000.000,00")
        this.composeTestRule.onNodeWithText("DEUDA").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("debt_total_COP").assertTextEquals("$800.000,00")
    }

    @Test
    fun given_no_debt_totals_when_displayed_then_no_debt_section_is_shown() {
        this.setScreen(this.content.copy(debtTotals = emptyList()))
        this.composeTestRule.onNodeWithTag("total_balance_COP").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("DEUDA").assertDoesNotExist()
        this.composeTestRule.onNodeWithTag("debt_total_COP").assertDoesNotExist()
    }

    @Test
    fun given_no_balance_totals_when_displayed_then_no_balance_section_is_shown_and_the_debt_is_shown() {
        this.setScreen(this.content.copy(balanceTotals = emptyList(), accounts = listOf(this.visaEntry)))
        this.composeTestRule.onNodeWithText("SALDO TOTAL").assertDoesNotExist()
        this.composeTestRule.onNodeWithTag("total_balance_COP").assertDoesNotExist()
        this.composeTestRule.onNodeWithText("DEUDA").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("debt_total_COP").assertTextEquals("$800.000,00")
    }

    @Test
    fun given_a_zero_debt_total_when_displayed_then_shows_deuda_zero() {
        this.setScreen(this.content.copy(debtTotals = listOf(pesos("0"))))
        this.composeTestRule.onNodeWithText("DEUDA").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("debt_total_COP").assertTextEquals("$0,00")
    }

    @Test
    fun given_a_credit_card_entry_when_displayed_then_shows_its_name_debt_and_available_credit() {
        this.setScreen(this.content.copy(accounts = listOf(this.visaEntry)))
        this.composeTestRule.onNodeWithText("Visa").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Deuda $500.000,00").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Disponible $2.500.000,00").assertIsDisplayed()
    }

    @Test
    fun given_a_money_account_entry_when_displayed_then_shows_its_name_type_and_balance() {
        this.setScreen(this.content)
        this.composeTestRule.onNodeWithText("Ahorros").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Ahorro").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("$150.000,00").assertIsDisplayed()
    }

    @Test
    fun given_more_accounts_when_displayed_then_shows_the_see_all_link() {
        this.setScreen(this.content.copy(hasMoreAccounts = true))
        this.composeTestRule.onNodeWithTag("see_all_accounts_action").assertIsDisplayed()
    }

    @Test
    fun given_a_credit_card_entry_when_selected_then_reports_its_id() {
        var selectedAccountId: String? = null
        this.setScreen(
            this.content.copy(accounts = listOf(this.savingsEntry, this.visaEntry)),
            onAccountSelected = { selectedAccountId = it }
        )
        this.composeTestRule.onNodeWithText("Visa").performClick()
        assertEquals("card-1", selectedAccountId)
    }

    @Test
    fun given_income_cannot_be_recorded_when_the_shortcut_is_expanded_then_income_is_not_shown() {
        this.setScreen(this.content.copy(accounts = listOf(this.visaEntry), canRecordIncome = false))
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("create_income_fab").assertDoesNotExist()
    }

    @Test
    fun given_income_can_be_recorded_when_the_shortcut_is_expanded_then_income_is_shown() {
        this.setScreen(this.content.copy(canRecordIncome = true))
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_income_fab").assertIsDisplayed()
    }

    @Test
    fun given_empty_state_when_displayed_then_shows_zero_balance_and_create_first_account() {
        this.setScreen(HomeUiState.Empty)
        this.composeTestRule.onNodeWithText("SALDO TOTAL").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("total_balance_zero").assertTextEquals("$0")
        this.composeTestRule.onNodeWithTag("create_first_account_action").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("DEUDA").assertDoesNotExist()
    }

    private fun setScreen(uiState: HomeUiState, onAccountSelected: (String) -> Unit = {}) {
        this.composeTestRule.setContent {
            HomeScreen(
                uiState = uiState,
                navigationEvent = emptyFlow(),
                onAccountSelected = onAccountSelected,
                onTransactionSelected = {},
                onCreateAccountSelected = {},
                onIncomeShortcutSelected = {},
                onExpenseShortcutSelected = {},
                onTransferShortcutSelected = {},
                onNavigateToAccountDetail = {},
                onNavigateToTransactionDetail = {},
                onNavigateToAccountCreate = {},
                onNavigateToIncomeCreate = {},
                onNavigateToExpenseCreate = {},
                onNavigateToTransferCreate = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {}
            )
        }
    }

    private fun pesos(amount: String) = Money.of(BigDecimal(amount), Currency.COP)
}
