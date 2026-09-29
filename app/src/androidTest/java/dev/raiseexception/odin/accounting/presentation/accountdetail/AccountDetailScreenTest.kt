package dev.raiseexception.odin.accounting.presentation.accountdetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.raiseexception.odin.accounting.application.usecase.AccountTransaction
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
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
        this.setScreen(this.cardContent(this.visaCard, canPay = true))
        this.composeTestRule.onNodeWithTag("expandable_fab").assertIsDisplayed()
    }

    @Test
    fun given_credit_card_that_can_be_paid_when_fab_expanded_then_offers_expense_and_payment_only() {
        this.setScreen(this.cardContent(this.visaCard, canPay = true))
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("create_payment_fab").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("create_income_fab").assertDoesNotExist()
        this.composeTestRule.onNodeWithTag("create_transfer_fab").assertDoesNotExist()
    }

    @Test
    fun given_credit_card_that_cannot_be_paid_when_fab_expanded_then_offers_only_expense() {
        this.setScreen(this.cardContent(this.visaCard, canPay = false))
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
        this.composeTestRule.onNodeWithTag("create_payment_fab").assertDoesNotExist()
        this.composeTestRule.onNodeWithTag("create_income_fab").assertDoesNotExist()
        this.composeTestRule.onNodeWithTag("create_transfer_fab").assertDoesNotExist()
    }

    @Test
    fun given_credit_card_with_no_debt_that_can_be_paid_when_fab_expanded_then_still_offers_payment() {
        val unusedCard = this.visaCard.copy(debt = pesos("0"), availableCredit = pesos("3000000.00"))
        this.setScreen(this.cardContent(unusedCard, canPay = true))
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_payment_fab").assertIsDisplayed()
    }

    @Test
    fun given_credit_card_that_can_be_paid_when_payment_selected_then_calls_create_transfer_callback() {
        var createTransferCalled = false
        this.setScreen(
            this.cardContent(this.visaCard, canPay = true),
            onCreateTransfer = { createTransferCalled = true }
        )
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_payment_fab").performClick()
        assertTrue(createTransferCalled)
    }

    @Test
    fun given_credit_card_content_when_expense_selected_then_calls_create_expense_callback() {
        var createExpenseCalled = false
        this.setScreen(
            this.cardContent(this.visaCard, canPay = true),
            onCreateExpense = { createExpenseCalled = true }
        )
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_expense_fab").performClick()
        assertTrue(createExpenseCalled)
    }

    @Test
    fun given_credit_card_with_no_available_credit_when_fab_expanded_then_still_offers_expense() {
        val maxedOutCard = this.visaCard.copy(debt = pesos("3000000.00"), availableCredit = pesos("0"))
        this.setScreen(this.cardContent(maxedOutCard, canPay = true))
        this.composeTestRule.onNodeWithTag("expandable_fab").performClick()
        this.composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
    }

    @Test
    fun given_credit_card_content_when_displayed_then_shows_no_edit() {
        this.setScreen(this.cardContent(this.visaCard, canPay = true))
        this.composeTestRule.onNodeWithTag("edit_account_button").assertDoesNotExist()
        this.composeTestRule.onNodeWithText("Editar").assertDoesNotExist()
    }

    @Test
    fun given_credit_card_content_when_displayed_then_shows_the_card_filters() {
        this.setScreen(this.cardContent(this.visaCard, canPay = true))
        this.composeTestRule.onNodeWithText("Todos").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Pagos").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Gastos").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Ingresos").assertDoesNotExist()
    }

    @Test
    fun given_credit_card_with_movements_under_all_when_displayed_then_shows_the_debt_after_each() {
        this.setScreen(
            this.cardContent(this.visaCard, canPay = true, transactions = this.cardMovementsWithDebts())
        )
        this.composeTestRule.onNodeWithText("Deuda: $400.000,00").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Deuda: $700.000,00").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Saldo:", substring = true).assertDoesNotExist()
    }

    @Test
    fun given_credit_card_with_movements_when_displayed_then_payment_is_plus_and_purchase_is_minus() {
        this.setScreen(
            this.cardContent(this.visaCard, canPay = true, transactions = this.cardMovementsWithDebts())
        )
        this.composeTestRule.onNodeWithText("+$300.000,00").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("-$200.000,00").assertIsDisplayed()
    }

    @Test
    fun given_credit_card_filtered_by_payments_with_none_when_displayed_then_shows_no_payments_message() {
        this.setScreen(
            this.cardContent(this.visaCard, canPay = true, activeFilter = TransactionFilter.INCOME)
        )
        this.composeTestRule.onNodeWithText("No hay pagos registrados").assertIsDisplayed()
    }

    @Test
    fun given_credit_card_filtered_by_purchases_with_none_when_displayed_then_shows_no_purchases_message() {
        this.setScreen(
            this.cardContent(this.visaCard, canPay = true, activeFilter = TransactionFilter.EXPENSE)
        )
        this.composeTestRule.onNodeWithText("No hay gastos registrados").assertIsDisplayed()
    }

    @Test
    fun given_credit_card_with_no_movements_when_displayed_then_shows_no_movements_message() {
        this.setScreen(this.cardContent(this.visaCard, canPay = true))
        this.composeTestRule.onNodeWithText("No hay movimientos registrados").assertIsDisplayed()
    }

    @Test
    fun given_credit_card_with_movements_when_a_movement_is_tapped_then_calls_transaction_selected_with_its_id() {
        var selectedTransactionId: String? = null
        this.setScreen(
            this.cardContent(this.visaCard, canPay = true, transactions = this.cardMovementsWithDebts()),
            onTransactionSelected = { selectedTransactionId = it }
        )
        this.composeTestRule.onNodeWithText("Mercado").performClick()
        assertEquals("purchase-1", selectedTransactionId)
    }

    @Test
    fun given_money_account_with_movements_when_displayed_then_shows_money_filters_and_balance() {
        val savingsAccount = Account.restore(
            id = "savings-1",
            name = "Ahorros",
            funding = AccountFunding.Funds(pesos("1000000.00")),
            type = AccountType.SAVINGS,
            description = "",
            createdAt = Instant.parse("2026-09-01T10:00:00Z")
        )
        val salary = this.income("salary-1", "Salario", "500000.00", "2026-09-10")
        this.setScreen(
            AccountDetailUiState.MoneyAccountContent(
                account = savingsAccount,
                initialBalance = pesos("1000000.00"),
                transactions = listOf(AccountTransaction(salary, pesos("1500000.00"))),
                activeFilter = TransactionFilter.ALL
            )
        )
        this.composeTestRule.onNodeWithText("Todos").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Ingresos").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Gastos").assertIsDisplayed()
        this.composeTestRule.onNodeWithText("Saldo: $1.500.000,00").assertIsDisplayed()
    }

    private fun cardContent(
        creditCard: CreditCardDetail,
        canPay: Boolean,
        transactions: List<AccountTransaction> = emptyList(),
        activeFilter: TransactionFilter = TransactionFilter.ALL
    ) = AccountDetailUiState.CreditCardContent(
        creditCard = creditCard,
        canPay = canPay,
        transactions = transactions,
        activeFilter = activeFilter
    )

    private fun cardMovementsWithDebts(): List<AccountTransaction> = listOf(
        AccountTransaction(
            this.income("payment-1", "Pago desde Ahorros", "300000.00", "2026-09-10"),
            pesos("400000.00")
        ),
        AccountTransaction(
            this.expense("purchase-1", "Mercado", "200000.00", "2026-09-05"),
            pesos("700000.00")
        )
    )

    private fun income(id: String, description: String, amount: String, date: String): Income = Income.restore(
        id = id,
        accountId = "visa-1",
        amount = pesos(amount),
        date = LocalDate.parse(date),
        categoryId = "category-1",
        description = description,
        createdAt = Instant.parse("${date}T10:00:00Z")
    )

    private fun expense(id: String, description: String, amount: String, date: String): Expense = Expense.restore(
        id = id,
        accountId = "visa-1",
        amount = pesos(amount),
        date = LocalDate.parse(date),
        categoryId = "category-2",
        description = description,
        createdAt = Instant.parse("${date}T10:00:00Z")
    )

    private fun setScreen(
        uiState: AccountDetailUiState,
        onCreateExpense: () -> Unit = {},
        onCreateTransfer: () -> Unit = {},
        onTransactionSelected: (String) -> Unit = {}
    ) {
        this.composeTestRule.setContent {
            AccountDetailScreen(
                uiState = uiState,
                navigationEvent = emptyFlow(),
                onCreateIncome = {},
                onCreateExpense = onCreateExpense,
                onCreateTransfer = onCreateTransfer,
                onNavigateToTransactionDetail = {},
                onEditAccount = {},
                onNavigateToEditAccount = {},
                onTransactionSelected = onTransactionSelected,
                onFilterChanged = {},
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {}
            )
        }
    }

    private fun pesos(amount: String) = Money.of(BigDecimal(amount), Currency.COP)
}
