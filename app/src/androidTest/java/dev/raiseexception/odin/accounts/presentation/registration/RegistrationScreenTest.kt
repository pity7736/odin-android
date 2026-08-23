package dev.raiseexception.odin.accounts.presentation.registration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegistrationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `given idle state, when displayed, then shows password fields submit and recommendation`() {
        composeTestRule.setContent {
            RegistrationScreen(
                uiState = RegistrationUiState.Idle,
                onRegister = { _, _ -> },
                navigationEvent = emptyFlow(),
                onRegistrationSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("password_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("password_confirmation_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("register_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recommendation_message").assertIsDisplayed()
    }

    @Test
    fun `given loading state, when displayed, then shows loading indicator`() {
        composeTestRule.setContent {
            RegistrationScreen(
                uiState = RegistrationUiState.Loading,
                onRegister = { _, _ -> },
                navigationEvent = emptyFlow(),
                onRegistrationSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `given success state, when displayed, then calls onRegistrationSuccess`() {
        val channel = Channel<NavigationTarget>(Channel.BUFFERED)
        var callbackInvoked = false
        composeTestRule.setContent {
            RegistrationScreen(
                uiState = RegistrationUiState.Success,
                onRegister = { _, _ -> },
                navigationEvent = channel.receiveAsFlow(),
                onRegistrationSuccess = { callbackInvoked = true }
            )
        }
        channel.trySend(NavigationTarget.Home)
        composeTestRule.waitUntil(timeoutMillis = 3000) { callbackInvoked }
        assertTrue(callbackInvoked)
    }

    @Test
    fun `given validation error with password error, when displayed, then shows error next to password field`() {
        composeTestRule.setContent {
            RegistrationScreen(
                uiState = RegistrationUiState.ValidationError(
                    passwordError = "La contraseña debe tener al menos 12 caracteres"
                ),
                onRegister = { _, _ -> },
                navigationEvent = emptyFlow(),
                onRegistrationSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("password_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("La contraseña debe tener al menos 12 caracteres").assertIsDisplayed()
    }

    @Test
    fun `given validation error with confirmation error, when displayed, then shows error next to confirmation field`() {
        composeTestRule.setContent {
            RegistrationScreen(
                uiState = RegistrationUiState.ValidationError(
                    passwordConfirmationError = "Las contraseñas no coinciden"
                ),
                onRegister = { _, _ -> },
                navigationEvent = emptyFlow(),
                onRegistrationSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("password_confirmation_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Las contraseñas no coinciden").assertIsDisplayed()
    }

    @Test
    fun `given error state, when displayed, then shows general error message`() {
        composeTestRule.setContent {
            RegistrationScreen(
                uiState = RegistrationUiState.Error(
                    message = "Algo salió mal. Intente de nuevo más tarde"
                ),
                onRegister = { _, _ -> },
                navigationEvent = emptyFlow(),
                onRegistrationSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("error_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Algo salió mal. Intente de nuevo más tarde").assertIsDisplayed()
    }

    @Test
    fun `given passwords typed, when register clicked, then onRegister receives both values`() {
        var capturedPassword = ""
        var capturedConfirmation = ""
        composeTestRule.setContent {
            RegistrationScreen(
                uiState = RegistrationUiState.Idle,
                onRegister = { password, confirmation ->
                    capturedPassword = password
                    capturedConfirmation = confirmation
                },
                navigationEvent = emptyFlow(),
                onRegistrationSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("password_field").performTextInput("mySecurePassword1")
        composeTestRule.onNodeWithTag("password_confirmation_field").performTextInput("mySecurePassword1")
        composeTestRule.onNodeWithTag("register_button").performClick()
        assertEquals("mySecurePassword1", capturedPassword)
        assertEquals("mySecurePassword1", capturedConfirmation)
    }
}
