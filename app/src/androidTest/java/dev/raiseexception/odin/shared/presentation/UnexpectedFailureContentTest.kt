package dev.raiseexception.odin.shared.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UnexpectedFailureContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_the_retry_content_when_displayed_then_shows_the_message_and_the_retry_action() {
        composeTestRule.setContent {
            UnexpectedFailureContent(onRetry = {})
        }
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    @Test
    fun given_the_retry_content_when_pressing_retry_then_calls_on_retry() {
        var retries = 0
        composeTestRule.setContent {
            UnexpectedFailureContent(onRetry = { retries++ })
        }
        composeTestRule.onNodeWithTag("unexpected_failure_retry").performClick()
        assertEquals(1, retries)
    }
}
