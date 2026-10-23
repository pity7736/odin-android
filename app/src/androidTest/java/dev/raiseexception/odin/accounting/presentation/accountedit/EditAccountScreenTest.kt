package dev.raiseexception.odin.accounting.presentation.accountedit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.shared.presentation.SelectedTag
import dev.raiseexception.odin.shared.presentation.TagSelection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditAccountScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun editing(
        locked: Boolean = false,
        nameError: String? = null,
        gmfExempt: Boolean = true
    ) = EditAccountUiState.Editing(
        name = "Ahorros",
        initialBalance = "1000.00",
        currency = Currency.COP,
        kind = MoneyAccountKind.SAVINGS,
        description = "Fondo",
        locked = locked,
        lockedBalanceDisplay = if (locked) "$1.000,00" else null,
        nameError = nameError,
        gmfExempt = gmfExempt,
        gmfCategoryName = if (gmfExempt) "" else "Impuestos",
        gmfCategoryId = if (gmfExempt) "" else "cat-impuestos",
        tagSelection = if (gmfExempt) TagSelection() else TagSelection(
            selected = listOf(SelectedTag("4x1000", TagInput.Existing("tag-4x1000")))
        )
    )

    @Test
    fun given_an_account_with_no_movements_when_editing_then_currency_and_balance_are_editable() {
        composeTestRule.setContent {
            EditAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = editing(locked = false),
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {}
            )
        }
        composeTestRule.onNodeWithTag("balance_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("currency_option_COP").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lock_message").assertDoesNotExist()
    }

    @Test
    fun given_an_account_with_movements_when_editing_then_currency_and_balance_are_read_only_and_lock_message_shown() {
        composeTestRule.setContent {
            EditAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = editing(locked = true),
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {}
            )
        }
        composeTestRule.onNodeWithTag("balance_readonly").assertIsDisplayed()
        composeTestRule.onNodeWithText("$1.000,00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("currency_readonly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("balance_field").assertDoesNotExist()
        composeTestRule.onNodeWithText(
            "No puedes cambiar la moneda ni el saldo inicial porque la cuenta ya tiene movimientos."
        ).assertIsDisplayed()
    }

    @Test
    fun given_a_field_error_when_editing_then_the_message_is_shown_next_to_the_field() {
        composeTestRule.setContent {
            EditAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = editing(nameError = "El nombre es obligatorio."),
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("El nombre es obligatorio.").assertIsDisplayed()
    }

    @Test
    fun given_the_not_found_state_then_the_not_found_message_is_shown() {
        composeTestRule.setContent {
            EditAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = EditAccountUiState.NotFound,
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {}
            )
        }
        composeTestRule.onNodeWithTag("not_found_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cuenta no encontrada").assertIsDisplayed()
    }

    @Test
    fun given_typed_text_and_an_unexpected_failure_when_accepting_the_message_then_the_typed_text_is_kept() {
        val unexpectedFailures = Channel<Unit>(Channel.BUFFERED)
        composeTestRule.setContent {
            EditAccountScreen(
                unexpectedFailureEvent = unexpectedFailures.receiveAsFlow(),
                onRetry = {},
                uiState = editing(),
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {},
            )
        }
        composeTestRule.onNodeWithTag("name_field").performTextInput("Nómina")
        unexpectedFailures.trySend(Unit)
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("name_field").assertExists()
        composeTestRule.onNodeWithText("Aceptar").performClick()
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertDoesNotExist()
        composeTestRule.onNodeWithTag("name_field").assertTextContains("Nómina", substring = true)
    }

    @Test
    fun given_an_unexpected_failure_when_displayed_then_shows_the_retry_content_and_no_information() {
        composeTestRule.setContent {
            EditAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = EditAccountUiState.UnexpectedFailure,
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {},
            )
        }
        composeTestRule.onNodeWithTag("unexpected_failure_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unexpected_failure_retry").assertIsDisplayed()
        composeTestRule.onNodeWithTag("name_field").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("not_found_message").assertDoesNotExist()
    }

    @Test
    fun given_a_savings_account_in_cop_that_existed_before_when_editing_then_the_exempt_option_is_on() {
        setScreen(editing())
        composeTestRule.onNodeWithText("Exenta de 4x1000").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gmf_exempt_switch").assertIsOn()
        composeTestRule.onNodeWithText("Categoría del 4x1000").assertDoesNotExist()
    }

    @Test
    fun given_Ahorros_not_exempt_when_editing_then_the_tax_category_and_tag_are_filled_in() {
        setScreen(editing(gmfExempt = false))
        composeTestRule.onNodeWithTag("gmf_exempt_switch").assertIsOff()
        composeTestRule.onNodeWithTag("category_field").assertTextContains("Impuestos")
        composeTestRule.onNodeWithText("4x1000").assertIsDisplayed()
    }

    @Test
    fun given_a_cash_account_when_editing_then_the_exempt_option_is_not_shown() {
        setScreen(editing().copy(kind = MoneyAccountKind.CASH))
        composeTestRule.onNodeWithText("Exenta de 4x1000").assertDoesNotExist()
    }

    @Test
    fun given_the_kind_chip_when_clicked_then_the_event_is_sent() {
        var selectedKind: MoneyAccountKind? = null
        composeTestRule.setContent {
            EditAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = editing(),
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {},
                events = EditAccountEvents(onKindSelected = { selectedKind = it })
            )
        }
        composeTestRule.onNodeWithTag("type_option_CASH").performClick()
        assertEquals(MoneyAccountKind.CASH, selectedKind)
    }

    @Test
    fun given_Ahorros_not_exempt_when_saving_then_the_tax_category_is_sent() {
        var captured: CategoryInput? = null
        composeTestRule.setContent {
            EditAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = editing(gmfExempt = false),
                onSave = { _, _, _, gmfCategoryInput -> captured = gmfCategoryInput },
                navigationEvent = emptyFlow(),
                onSaved = {}
            )
        }
        composeTestRule.onNodeWithTag("save_button").performClick()
        assertEquals(CategoryInput.New("Impuestos"), captured)
    }

    private fun setScreen(uiState: EditAccountUiState) {
        composeTestRule.setContent {
            EditAccountScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = uiState,
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {}
            )
        }
    }
}
