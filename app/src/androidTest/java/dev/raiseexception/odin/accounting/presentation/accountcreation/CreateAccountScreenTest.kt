package dev.raiseexception.odin.accounting.presentation.accountcreation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateAccountScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val impuestos = Category.restore(
        id = "cat-impuestos",
        name = "Impuestos",
        type = CategoryType.EXPENSE,
        description = "",
        color = "#FF0000",
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )
    private val savingsInCop = AccountForm(type = AccountType.SAVINGS, currency = Currency.COP)

    @Test
    fun given_idle_state_when_displayed_then_shows_the_fields_and_the_create_action() {
        setScreen(CreateAccountUiState.Idle(AccountForm()))
        composeTestRule.onNodeWithTag("name_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_option_SAVINGS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_button").assertIsDisplayed()
    }

    @Test
    fun given_loading_state_when_displayed_then_shows_the_loading_indicator() {
        setScreen(CreateAccountUiState.Loading)
        composeTestRule.onNodeWithTag("form_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun given_saving_state_when_displayed_then_shows_the_saving_indicator() {
        setScreen(CreateAccountUiState.Saving(savingsInCop))
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun given_a_navigation_event_when_received_then_calls_onCreateSuccess() {
        val channel = Channel<NavigationTarget>(Channel.BUFFERED)
        var callbackInvoked = false
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateAccountUiState.Idle(AccountForm()),
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
        setScreen(
            CreateAccountUiState.ValidationError(
                form = AccountForm(type = AccountType.SAVINGS),
                nameError = "El nombre es obligatorio.",
                balanceError = "El saldo inicial no puede ser negativo.",
                currencyError = "La moneda es obligatoria.",
                typeError = "El tipo de cuenta es obligatorio.",
                descriptionError = "La descripción no puede superar los 500 caracteres."
            )
        )
        composeTestRule.onNodeWithTag("name_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("balance_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("currency_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field_error").assertIsDisplayed()
    }

    @Test
    fun given_an_error_state_when_displayed_then_shows_the_general_error_message() {
        setScreen(CreateAccountUiState.Error("Algo salió mal. Intente de nuevo más tarde"))
        composeTestRule.onNodeWithTag("error_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Algo salió mal. Intente de nuevo más tarde").assertIsDisplayed()
    }

    @Test
    fun given_a_failed_save_when_displayed_then_shows_the_message_under_the_form() {
        setScreen(CreateAccountUiState.SaveFailed(AccountForm(), "No se pudo guardar"))
        composeTestRule.onNodeWithTag("name_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_error_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("No se pudo guardar").assertIsDisplayed()
    }

    @Test
    fun given_typed_fields_when_the_state_changes_to_saving_and_to_a_failed_save_then_the_fields_keep_their_text() {
        val uiState = mutableStateOf<CreateAccountUiState>(CreateAccountUiState.Idle(AccountForm()))
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = uiState.value,
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field").performTextInput("Ahorros")
        uiState.value = CreateAccountUiState.Saving(AccountForm())
        uiState.value = CreateAccountUiState.SaveFailed(AccountForm(), "No se pudo guardar")
        composeTestRule.onNodeWithTag("name_field").assertTextContains("Ahorros")
    }

    @Test
    fun given_type_and_currency_chips_when_clicked_then_the_events_are_sent() {
        var selectedType: AccountType? = null
        var selectedCurrency: Currency? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateAccountUiState.Idle(AccountForm()),
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {},
                onTypeSelected = { selectedType = it },
                onCurrencySelected = { selectedCurrency = it }
            )
        }
        composeTestRule.onNodeWithTag("type_option_SAVINGS").performClick()
        composeTestRule.onNodeWithTag("currency_option_COP").performClick()
        assertEquals(AccountType.SAVINGS, selectedType)
        assertEquals(Currency.COP, selectedCurrency)
    }

    @Test
    fun given_filled_fields_when_create_clicked_then_onCreate_receives_the_typed_values() {
        var captured: AccountFormFields? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateAccountUiState.Idle(savingsInCop),
                onCreate = { captured = it },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field").performTextInput("Ahorros")
        composeTestRule.onNodeWithTag("balance_field").performTextInput("1500,00")
        composeTestRule.onNodeWithTag("description_field").performTextInput("Fondo de emergencia")
        composeTestRule.onNodeWithTag("create_button").performClick()
        val fields = captured!!
        assertEquals("Ahorros", fields.name)
        assertEquals("1500,00", fields.balance)
        assertEquals("Fondo de emergencia", fields.description)
    }

    @Test
    fun given_balance_field_when_user_types_1500000_then_field_displays_formatted_amount_with_thousand_separators() {
        setScreen(CreateAccountUiState.Idle(savingsInCop))
        composeTestRule.onNodeWithTag("balance_field").performTextInput("1500000")
        composeTestRule.onNodeWithText("1.500.000").assertIsDisplayed()
    }

    @Test
    fun given_a_savings_account_in_cop_when_displayed_then_the_exempt_option_is_shown_on() {
        setScreen(CreateAccountUiState.Idle(savingsInCop))
        composeTestRule.onNodeWithText("Exenta de 4x1000").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gmf_exempt_switch").assertIsOn()
        composeTestRule.onNodeWithText("Categoría del 4x1000").assertDoesNotExist()
        composeTestRule.onNodeWithText("Etiquetas del 4x1000").assertDoesNotExist()
    }

    @Test
    fun given_a_cash_account_when_displayed_then_the_exempt_option_is_not_shown() {
        setScreen(CreateAccountUiState.Idle(AccountForm(type = AccountType.CASH, currency = Currency.COP)))
        composeTestRule.onNodeWithText("Exenta de 4x1000").assertDoesNotExist()
    }

    @Test
    fun given_the_option_off_when_displayed_then_the_tax_category_and_tags_are_shown() {
        setScreen(CreateAccountUiState.Idle(savingsInCop.copy(gmfExempt = false, categories = listOf(impuestos))))
        composeTestRule.onNodeWithTag("gmf_exempt_switch").assertIsOff()
        composeTestRule.onNodeWithText("Categoría del 4x1000").assertIsDisplayed()
        composeTestRule.onNodeWithText("Etiquetas del 4x1000").assertIsDisplayed()
    }

    @Test
    fun given_the_option_off_and_Impuestos_typed_when_creating_then_the_tax_category_is_sent() {
        var captured: AccountFormFields? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateAccountUiState.Idle(
                    savingsInCop.copy(gmfExempt = false, categories = listOf(impuestos))
                ),
                onCreate = { captured = it },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("category_field").performTextInput("Impuestos")
        composeTestRule.onNodeWithTag("create_button").performScrollTo().performClick()
        assertEquals(CategoryInput.Existing("cat-impuestos"), captured!!.gmfCategoryInput)
    }

    @Test
    fun given_a_missing_tax_category_when_displayed_then_the_message_is_shown_next_to_it() {
        setScreen(
            CreateAccountUiState.ValidationError(
                form = savingsInCop.copy(gmfExempt = false),
                gmfCategoryError = "La categoría del 4x1000 es obligatoria."
            )
        )
        composeTestRule.onNodeWithText("La categoría del 4x1000 es obligatoria.").assertIsDisplayed()
    }

    @Test
    fun given_the_exempt_switch_when_toggled_then_the_event_is_sent() {
        var gmfExempt: Boolean? = null
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateAccountUiState.Idle(savingsInCop),
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {},
                onGmfExemptChanged = { gmfExempt = it }
            )
        }
        composeTestRule.onNodeWithTag("gmf_exempt_switch").performClick()
        assertEquals(false, gmfExempt)
    }

    @Test
    fun given_typed_text_and_an_unexpected_failure_when_accepting_the_message_then_the_typed_text_is_kept() {
        val unexpectedFailures = Channel<Unit>(Channel.BUFFERED)
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = unexpectedFailures.receiveAsFlow(),
                uiState = CreateAccountUiState.Idle(AccountForm()),
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {},
            )
        }
        composeTestRule.onNodeWithTag("name_field").performTextInput("Ahorros")
        unexpectedFailures.trySend(Unit)
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("name_field").assertExists()
        composeTestRule.onNodeWithText("Aceptar").performClick()
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertDoesNotExist()
        composeTestRule.onNodeWithTag("name_field").assertTextContains("Ahorros", substring = true)
    }

    private fun setScreen(uiState: CreateAccountUiState) {
        composeTestRule.setContent {
            CreateAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = uiState,
                onCreate = {},
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
    }
}
