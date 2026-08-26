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
    fun given_idle_state_when_displayed_then_shows_password_fields_submit_and_recommendation() {
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
    fun given_loading_state_when_displayed_then_shows_loading_indicator() {
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
    fun given_navigation_event_when_received_then_calls_onRegistrationSuccess() {
        val channel = Channel<NavigationTarget>(Channel.BUFFERED)
        var callbackInvoked = false
        composeTestRule.setContent {
            RegistrationScreen(
                uiState = RegistrationUiState.Idle,
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
    fun given_validation_error_with_password_error_when_displayed_then_shows_error_next_to_password_field() {
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
    fun given_validation_error_with_confirmation_error_when_displayed_then_shows_error_next_to_confirmation_field() {
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
    fun given_error_state_when_displayed_then_shows_general_error_message() {
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
    fun given_passwords_typed_when_register_clicked_then_onRegister_receives_both_values() {
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
