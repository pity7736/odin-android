package dev.raiseexception.odin.accounting.presentation.transfercreation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Rule
import org.junit.Test

class CreateTransferScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_typed_text_and_an_unexpected_failure_when_accepting_the_message_then_the_typed_text_is_kept() {
        val unexpectedFailures = Channel<Unit>(Channel.BUFFERED)
        composeTestRule.setContent {
            CreateTransferScreen(
                unexpectedFailureEvent = unexpectedFailures.receiveAsFlow(),
                onRetry = {},
                uiState = CreateTransferUiState.Idle(
                    sourceAccounts = emptyList(),
                    destinationAccounts = emptyList(),
                    selectedSourceAccountId = "",
                    selectedDestinationAccountId = "",
                    saveLabel = "Transferir",
                ),
                onSourceSelected = {},
                onDestinationSelected = {},
                onSave = { _, _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {},
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
                onSave = { _, _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {},
            )
        }
        composeTestRule.onNodeWithTag("unexpected_failure_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unexpected_failure_retry").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_field").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_button").assertDoesNotExist()
    }
}
