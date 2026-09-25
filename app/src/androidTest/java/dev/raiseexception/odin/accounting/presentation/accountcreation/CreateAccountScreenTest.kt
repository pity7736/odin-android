package dev.raiseexception.odin.accounting.presentation.accountcreation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.raiseexception.odin.accounting.application.usecase.CreateAccountCommand
import dev.raiseexception.odin.accounting.domain.model.Currency
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateAccountScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_idle_state_when_displayed_then_shows_the_fields_and_the_create_action() {
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Idle,
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_option_SAVINGS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_button").assertIsDisplayed()
    }

    @Test
    fun given_loading_state_when_displayed_then_shows_the_loading_indicator() {
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Loading,
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun given_a_navigation_event_when_received_then_calls_onCreateSuccess() {
        val channel = Channel<NavigationTarget>(Channel.BUFFERED)
        var callbackInvoked = false
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Idle,
                onCreate = {},
                navigationEvent = channel.receiveAsFlow(),
                onCreateSuccess = { callbackInvoked = true }
            )
        }
        channel.trySend(NavigationTarget.AccountsList)
        composeTestRule.waitUntil(timeoutMillis = 3000) { callbackInvoked }
        assertTrue(callbackInvoked)
    }

    @Test
    fun given_a_validation_error_when_displayed_then_shows_each_field_message() {
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.ValidationError(
                    nameError = "El nombre es obligatorio.",
                    balanceError = "El saldo inicial no puede ser negativo.",
                    currencyError = "La moneda es obligatoria.",
                    typeError = "El tipo de cuenta es obligatorio.",
                    descriptionError = "La descripción no puede superar los 500 caracteres."
                ),
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("type_option_SAVINGS").performClick()
        composeTestRule.onNodeWithTag("name_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("balance_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("currency_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field_error").assertIsDisplayed()
    }

    @Test
    fun given_an_error_state_when_displayed_then_shows_the_general_error_message() {
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Error("Algo salió mal. Intente de nuevo más tarde"),
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("error_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Algo salió mal. Intente de nuevo más tarde").assertIsDisplayed()
    }

    @Test
    fun given_filled_fields_when_create_clicked_then_onCreate_receives_a_money_command_with_the_typed_values() {
        var captured: CreateAccountCommand? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Idle,
                onCreate = { captured = it },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field").performTextInput("Ahorros")
        composeTestRule.onNodeWithTag("type_option_SAVINGS").performClick()
        composeTestRule.onNodeWithTag("currency_option_COP").performClick()
        composeTestRule.onNodeWithTag("balance_field").performTextInput("1500,00")
        composeTestRule.onNodeWithTag("description_field").performTextInput("Fondo de emergencia")
        composeTestRule.onNodeWithTag("create_button").performClick()
        val money = captured as CreateAccountCommand.MoneyAccount
        assertEquals("Ahorros", money.name)
        assertEquals("1500.00", money.balance)
        assertEquals(Currency.COP, money.currency)
        assertEquals("Fondo de emergencia", money.description)
    }

    @Test
    fun given_balance_field_when_user_types_1500000_then_field_displays_formatted_amount_with_thousand_separators() {
        var captured: CreateAccountCommand? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Idle,
                onCreate = { captured = it },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("type_option_SAVINGS").performClick()
        composeTestRule.onNodeWithTag("balance_field").performTextInput("1500000")
        composeTestRule.onNodeWithText("1.500.000").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_button").performClick()
        assertEquals("1500000", (captured as CreateAccountCommand.MoneyAccount).balance)
    }

    @Test
    fun given_balance_field_when_user_types_a_comma_decimal_then_it_is_formatted_and_saved_as_a_dot_decimal() {
        var captured: CreateAccountCommand? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Idle,
                onCreate = { captured = it },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("type_option_SAVINGS").performClick()
        composeTestRule.onNodeWithTag("balance_field").performTextInput("111176,46")
        composeTestRule.onNodeWithText("111.176,46").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_button").performClick()
        assertEquals("111176.46", (captured as CreateAccountCommand.MoneyAccount).balance)
    }
}
