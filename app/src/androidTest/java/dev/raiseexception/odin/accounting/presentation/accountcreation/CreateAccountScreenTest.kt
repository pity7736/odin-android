package dev.raiseexception.odin.accounting.presentation.accountcreation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.raiseexception.odin.accounting.domain.model.AccountType
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
    fun `given idle state, when displayed, then shows the fields and the create action`() {
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Idle,
                onCreate = { _, _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("balance_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_button").assertIsDisplayed()
    }

    @Test
    fun `given loading state, when displayed, then shows the loading indicator`() {
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Loading,
                onCreate = { _, _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `given a navigation event, when received, then calls onCreateSuccess`() {
        val channel = Channel<NavigationTarget>(Channel.BUFFERED)
        var callbackInvoked = false
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Idle,
                onCreate = { _, _, _, _, _ -> },
                navigationEvent = channel.receiveAsFlow(),
                onCreateSuccess = { callbackInvoked = true }
            )
        }
        channel.trySend(NavigationTarget.AccountsList)
        composeTestRule.waitUntil(timeoutMillis = 3000) { callbackInvoked }
        assertTrue(callbackInvoked)
    }

    @Test
    fun `given a validation error, when displayed, then shows each field message`() {
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.ValidationError(
                    nameError = "El nombre es obligatorio.",
                    balanceError = "El saldo inicial no puede ser negativo.",
                    currencyError = "La moneda es obligatoria.",
                    typeError = "El tipo de cuenta es obligatorio.",
                    descriptionError = "La descripción no puede superar los 500 caracteres."
                ),
                onCreate = { _, _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("balance_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("currency_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field_error").assertIsDisplayed()
    }

    @Test
    fun `given an error state, when displayed, then shows the general error message`() {
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Error("Algo salió mal. Intente de nuevo más tarde"),
                onCreate = { _, _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("error_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Algo salió mal. Intente de nuevo más tarde").assertIsDisplayed()
    }

    @Test
    fun `given filled fields, when create clicked, then onCreate receives the typed values`() {
        var capturedName = ""
        var capturedBalance = ""
        var capturedCurrency: Currency? = null
        var capturedType: AccountType? = null
        var capturedDescription = ""
        composeTestRule.setContent {
            CreateAccountScreen(
                uiState = CreateAccountUiState.Idle,
                onCreate = { name, balance, currency, type, description ->
                    capturedName = name
                    capturedBalance = balance
                    capturedCurrency = currency
                    capturedType = type
                    capturedDescription = description
                },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field").performTextInput("Ahorros")
        composeTestRule.onNodeWithTag("balance_field").performTextInput("1500.00")
        composeTestRule.onNodeWithTag("description_field").performTextInput("Fondo de emergencia")
        composeTestRule.onNodeWithTag("currency_option_COP").performClick()
        composeTestRule.onNodeWithTag("type_option_SAVINGS").performClick()
        composeTestRule.onNodeWithTag("create_button").performClick()
        assertEquals("Ahorros", capturedName)
        assertEquals("1500.00", capturedBalance)
        assertEquals(Currency.COP, capturedCurrency)
        assertEquals(AccountType.SAVINGS, capturedType)
        assertEquals("Fondo de emergencia", capturedDescription)
    }
}
