package dev.raiseexception.odin.accounting.presentation.transfercreation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreateTransferScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_typed_text_and_an_unexpected_failure_when_accepting_the_message_then_the_typed_text_is_kept() {
        val unexpectedFailures = Channel<Unit>(Channel.BUFFERED)
        var uiState: CreateTransferUiState by mutableStateOf(this.idle())
        composeTestRule.setContent {
            CreateTransferScreen(
                unexpectedFailureEvent = unexpectedFailures.receiveAsFlow(),
                onRetry = {},
                uiState = uiState,
                onSourceSelected = {},
                onDestinationSelected = {},
                onSave = { _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {},
                onAmountChange = { amount -> uiState = (uiState as CreateTransferUiState.Idle).copy(amount = amount) },
            )
        }
        composeTestRule.onNodeWithTag("amount_field").performTextInput("200")
        unexpectedFailures.trySend(Unit)
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_field").assertExists()
        composeTestRule.onNodeWithText("Aceptar").performClick()
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertDoesNotExist()
        composeTestRule.onNodeWithTag("amount_field").assertTextContains("200", substring = true)
    }

    @Test
    fun given_an_unexpected_failure_when_displayed_then_shows_the_retry_content_and_no_information() {
        composeTestRule.setContent {
            CreateTransferScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = CreateTransferUiState.UnexpectedFailure,
                onSourceSelected = {},
                onDestinationSelected = {},
                onSave = { _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {},
            )
        }
        composeTestRule.onNodeWithTag("unexpected_failure_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unexpected_failure_retry").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_field").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_button").assertDoesNotExist()
    }

    @Test
    fun given_a_non_exempt_source_when_displayed_then_the_charge_option_is_shown_on_with_its_note() {
        setScreen(this.idle().copy(showsGmfOption = true, chargeGmf = true))
        composeTestRule.onNodeWithText("Cobrar 4x1000").assertIsDisplayed()
        composeTestRule.onNodeWithTag("charge_gmf_switch").assertIsOn()
        composeTestRule.onNodeWithText("Desactívalo si transfieres entre tus cuentas del mismo banco.").assertIsDisplayed()
    }

    @Test
    fun given_an_exempt_source_when_displayed_then_the_charge_option_is_not_shown() {
        setScreen(this.idle())
        composeTestRule.onNodeWithText("Cobrar 4x1000").assertDoesNotExist()
    }

    @Test
    fun given_the_charge_option_when_toggled_then_the_event_is_sent() {
        var chargeGmf: Boolean? = null
        composeTestRule.setContent {
            CreateTransferScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = this.idle().copy(showsGmfOption = true, chargeGmf = true),
                onSourceSelected = {},
                onDestinationSelected = {},
                onSave = { _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {},
                onChargeGmfChange = { chargeGmf = it },
            )
        }
        composeTestRule.onNodeWithTag("charge_gmf_switch").performClick()
        assertEquals(false, chargeGmf)
    }

    @Test
    fun given_a_tax_line_when_displayed_then_it_is_shown_under_the_amount() {
        setScreen(this.idle().copy(showsGmfOption = true, amount = "200000", gmfTaxLine = "4x1000: $800,00"))
        composeTestRule.onNodeWithTag("gmf_tax_line").assertTextContains("4x1000: $800,00")
    }

    private fun setScreen(uiState: CreateTransferUiState) {
        composeTestRule.setContent {
            CreateTransferScreen(
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                uiState = uiState,
                onSourceSelected = {},
                onDestinationSelected = {},
                onSave = { _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {},
            )
        }
    }

    private fun idle() = CreateTransferUiState.Idle(
        sourceAccounts = emptyList(),
        destinationAccounts = emptyList(),
        selectedSourceAccountId = "",
        selectedDestinationAccountId = "",
        saveLabel = "Transferir",
    )
}
