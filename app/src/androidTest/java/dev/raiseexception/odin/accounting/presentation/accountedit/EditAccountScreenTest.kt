package dev.raiseexception.odin.accounting.presentation.accountedit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test

class EditAccountScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun editing(
        locked: Boolean = false,
        nameError: String? = null
    ) = EditAccountUiState.Editing(
        name = "Ahorros",
        initialBalance = "1000.00",
        currency = Currency.COP,
        type = AccountType.SAVINGS,
        description = "Fondo",
        locked = locked,
        lockedBalanceDisplay = if (locked) "$1.000,00" else null,
        nameError = nameError
    )

    @Test
    fun given_an_account_with_no_movements_when_editing_then_currency_and_balance_are_editable() {
        composeTestRule.setContent {
            EditAccountScreen(
                uiState = editing(locked = false),
                onSave = { _, _, _, _, _ -> },
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
                uiState = editing(locked = true),
                onSave = { _, _, _, _, _ -> },
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
                uiState = editing(nameError = "El nombre es obligatorio."),
                onSave = { _, _, _, _, _ -> },
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
                uiState = EditAccountUiState.NotFound,
                onSave = { _, _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {}
            )
        }
        composeTestRule.onNodeWithTag("not_found_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cuenta no encontrada").assertIsDisplayed()
    }
}
