package dev.raiseexception.odin.accounting.presentation.accountslist

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class AccountsListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val savingsAccount = Account.restore(
        id = "acc-1",
        name = "Ahorros",
        funding = AccountFunding.Funds(pesos("150000.00")),
        type = AccountType.SAVINGS,
        description = "",
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private val visaCard = CreditCardItem(
        id = "card-1",
        name = "Visa",
        debt = pesos("500000.00"),
        availableCredit = pesos("2500000.00")
    )

    @Test
    fun given_money_accounts_and_credit_cards_when_displayed_then_shows_accounts_group_before_credit_cards_group() {
        this.setScreen(
            AccountsListUiState.Content(moneyAccounts = listOf(this.savingsAccount), creditCards = listOf(this.visaCard))
        )
        val accountsHeader = this.composeTestRule.onNodeWithTag("accounts_group_header")
        val creditCardsHeader = this.composeTestRule.onNodeWithTag("credit_cards_group_header")
        accountsHeader.assertIsDisplayed().assertTextEquals("Cuentas")
        creditCardsHeader.assertIsDisplayed().assertTextEquals("Tarjetas de crédito")
        assertTrue(accountsHeader.getUnclippedBoundsInRoot().top < creditCardsHeader.getUnclippedBoundsInRoot().top)
    }

    @Test
    fun given_a_money_account_when_displayed_then_shows_its_name_and_balance() {
        this.setScreen(AccountsListUiState.Content(moneyAccounts = listOf(this.savingsAccount), creditCards = emptyList()))
        this.composeTestRule.onNodeWithText("Ahorros").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("$150.000,00").assertIsDisplayed()
    }

    @Test
    fun given_a_credit_card_with_debt_when_displayed_then_shows_its_name_debt_and_available_credit() {
        this.setScreen(AccountsListUiState.Content(moneyAccounts = emptyList(), creditCards = listOf(this.visaCard)))
        this.composeTestRule.onNodeWithText("Visa").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Deuda $500.000,00").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Disponible $2.500.000,00").assertIsDisplayed()
    }

    @Test
    fun given_a_credit_card_with_no_debt_when_displayed_then_shows_zero_debt_and_the_full_limit_available() {
        val unusedCard = this.visaCard.copy(debt = pesos("0"), availableCredit = pesos("3000000.00"))
        this.setScreen(AccountsListUiState.Content(moneyAccounts = emptyList(), creditCards = listOf(unusedCard)))
        this.composeTestRule.onNodeWithText("Deuda $0,00").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Disponible $3.000.000,00").assertIsDisplayed()
    }

    @Test
    fun given_a_credit_card_whose_debt_equals_its_limit_when_displayed_then_shows_zero_available_credit() {
        val maxedOutCard = this.visaCard.copy(debt = pesos("3000000.00"), availableCredit = pesos("0"))
        this.setScreen(AccountsListUiState.Content(moneyAccounts = emptyList(), creditCards = listOf(maxedOutCard)))
        this.composeTestRule.onNodeWithText("Deuda $3.000.000,00").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Disponible $0,00").assertIsDisplayed()
    }

    @Test
    fun given_only_money_accounts_when_displayed_then_the_credit_cards_group_is_not_shown() {
        this.setScreen(AccountsListUiState.Content(moneyAccounts = listOf(this.savingsAccount), creditCards = emptyList()))
        this.composeTestRule.onNodeWithTag("accounts_group_header").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("credit_cards_group_header").assertDoesNotExist()
    }

    @Test
    fun given_only_credit_cards_when_displayed_then_the_accounts_group_is_not_shown() {
        this.setScreen(AccountsListUiState.Content(moneyAccounts = emptyList(), creditCards = listOf(this.visaCard)))
        this.composeTestRule.onNodeWithTag("credit_cards_group_header").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("accounts_group_header").assertDoesNotExist()
    }

    @Test
    fun given_a_money_account_when_selected_then_onAccountSelected_receives_its_id() {
        var selectedAccountId: String? = null
        this.setScreen(
            AccountsListUiState.Content(moneyAccounts = listOf(this.savingsAccount), creditCards = emptyList()),
            onAccountSelected = { selectedAccountId = it }
        )
        this.composeTestRule.onNodeWithText("Ahorros").performClick()
        assertEquals("acc-1", selectedAccountId)
    }

    @Test
    fun given_a_credit_card_when_displayed_then_its_row_cannot_be_selected() {
        var selectedAccountId: String? = null
        this.setScreen(
            AccountsListUiState.Content(moneyAccounts = emptyList(), creditCards = listOf(this.visaCard)),
            onAccountSelected = { selectedAccountId = it }
        )
        this.composeTestRule.onNodeWithTag("credit_card_row_card-1").assertHasNoClickAction()
        this.composeTestRule.onNodeWithText("Visa").performClick()
        assertEquals(null, selectedAccountId)
    }

    @Test
    fun given_no_accounts_when_displayed_then_shows_the_empty_message_and_no_group_headers() {
        this.setScreen(AccountsListUiState.Empty)
        this.composeTestRule.onNodeWithText("No hay cuentas registradas").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("create_account_fab").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("accounts_group_header").assertDoesNotExist()
        this.composeTestRule.onNodeWithTag("credit_cards_group_header").assertDoesNotExist()
    }

    private fun setScreen(uiState: AccountsListUiState, onAccountSelected: (String) -> Unit = {}) {
        this.composeTestRule.setContent {
            AccountsListScreen(
                uiState = uiState,
                navigationEvent = emptyFlow(),
                onCreateAccount = {},
                onAccountSelected = onAccountSelected,
                onNavigateToAccountDetail = {},
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {}
            )
        }
    }

    private fun pesos(amount: String) = Money.of(BigDecimal(amount), Currency.COP)
}
