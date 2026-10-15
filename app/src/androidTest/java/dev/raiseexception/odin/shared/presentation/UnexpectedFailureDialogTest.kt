package dev.raiseexception.odin.shared.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UnexpectedFailureDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_the_dialog_when_displayed_then_shows_the_message_and_the_accept_action() {
        composeTestRule.setContent {
            UnexpectedFailureDialog(onDismiss = {})
        }
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Aceptar").assertIsDisplayed()
    }

    @Test
    fun given_the_dialog_when_pressing_accept_then_calls_on_dismiss() {
        var dismissals = 0
        composeTestRule.setContent {
            UnexpectedFailureDialog(onDismiss = { dismissals++ })
        }
        composeTestRule.onNodeWithTag("unexpected_failure_dismiss").performClick()
        assertEquals(1, dismissals)
    }
}
