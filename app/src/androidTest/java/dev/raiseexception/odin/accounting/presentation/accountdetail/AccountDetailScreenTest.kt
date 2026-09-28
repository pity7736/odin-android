package dev.raiseexception.odin.accounting.presentation.accountdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class AccountDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val visaCard = CreditCardDetail(
        name = "Visa",
        debt = pesos("500000.00"),
        availableCredit = pesos("2500000.00"),
        creditLimit = pesos("3000000.00")
    )

    @Test
    fun given_credit_card_content_when_displayed_then_shows_the_fab() {
        this.setScreen(AccountDetailUiState.CreditCardContent(this.visaCard))
        this.composeTestRule.onNodeWithTag("expandable_fab").assertIsDisplayed()
    }

    @Test
    fun given_credit_card_content_when_fab_expanded_then_offers_only_expense() {
        this.setScreen(AccountDetailUiState.CreditCardContent(this.visaCard))
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("create_income_fab").assertDoesNotExist()
        this.composeTestRule.onNodeWithTag("create_transfer_fab").assertDoesNotExist()
    }

    @Test
    fun given_credit_card_content_when_expense_selected_then_calls_create_expense_callback() {
        var createExpenseCalled = false
        this.setScreen(
            AccountDetailUiState.CreditCardContent(this.visaCard),
            onCreateExpense = { createExpenseCalled = true }
        )
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_expense_fab").performClick()
        assertTrue(createExpenseCalled)
    }

    @Test
    fun given_credit_card_with_no_available_credit_when_fab_expanded_then_still_offers_expense() {
        val maxedOutCard = this.visaCard.copy(debt = pesos("3000000.00"), availableCredit = pesos("0"))
        this.setScreen(AccountDetailUiState.CreditCardContent(maxedOutCard))
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
    }

    @Test
    fun given_credit_card_content_when_displayed_then_shows_no_edit_filters_or_movements() {
        this.setScreen(AccountDetailUiState.CreditCardContent(this.visaCard))
        this.composeTestRule.onNodeWithTag("edit_account_button").assertDoesNotExist()
        this.composeTestRule.onNodeWithText("Todos").assertDoesNotExist()
        this.composeTestRule.onNodeWithText("Ingresos").assertDoesNotExist()
        this.composeTestRule.onNodeWithText("Gastos").assertDoesNotExist()
        this.composeTestRule.onNodeWithTag("empty_transactions_message").assertDoesNotExist()
    }

    private fun setScreen(uiState: AccountDetailUiState, onCreateExpense: () -> Unit = {}) {
        this.composeTestRule.setContent {
            AccountDetailScreen(
                uiState = uiState,
                navigationEvent = emptyFlow(),
                onCreateIncome = {},
                onCreateExpense = onCreateExpense,
                onCreateTransfer = {},
                onNavigateToTransactionDetail = {},
                onEditAccount = {},
                onNavigateToEditAccount = {},
                onTransactionSelected = {},
                onFilterChanged = {},
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {}
            )
        }
    }

    private fun pesos(amount: String) = Money.of(BigDecimal(amount), Currency.COP)
}
