package dev.raiseexception.odin.accounting.presentation.accountcreation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreateCreditCardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_credit_card_type_selected_when_displayed_then_shows_credit_limit_and_debt_fields() {
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateAccountUiState.Idle(AccountForm(type = AccountType.CREDIT_CARD)),
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("credit_limit_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("debt_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("balance_field").assertDoesNotExist()
    }

    @Test
    fun given_filled_credit_card_fields_when_create_clicked_then_onCreate_receives_a_credit_card_command() {
        var captured: AccountFormFields? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateAccountUiState.Idle(AccountForm(type = AccountType.CREDIT_CARD, currency = Currency.COP)),
                onCreate = { captured = it },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field").performTextInput("Visa")
        composeTestRule.onNodeWithTag("credit_limit_field").performTextInput("3000000")
        composeTestRule.onNodeWithTag("debt_field").performTextClearance()
        composeTestRule.onNodeWithTag("debt_field").performTextInput("500000")
        composeTestRule.onNodeWithTag("create_button").performClick()
        val fields = captured!!
        assertEquals("Visa", fields.name)
        assertEquals("3000000", fields.creditLimit)
        assertEquals("500000", fields.debt)
    }

    @Test
    fun given_credit_card_validation_errors_when_displayed_then_shows_credit_limit_and_debt_messages() {
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateAccountUiState.ValidationError(
                    form = AccountForm(type = AccountType.CREDIT_CARD),
                    creditLimitError = "El cupo es obligatorio.",
                    debtError = "La deuda actual no puede ser negativa."
                ),
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("credit_limit_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("debt_field_error").assertIsDisplayed()
    }
}
