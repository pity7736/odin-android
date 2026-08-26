package dev.raiseexception.odin.accounts.presentation.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_idle_state_when_displayed_then_shows_password_field_and_submit_hidden_by_default() {
        composeTestRule.setContent {
            LoginScreen(
                uiState = LoginUiState.Idle,
                onLogin = {},
                navigationEvent = emptyFlow(),
                onLoginSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("password_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reveal_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mostrar contraseña").assertIsDisplayed()
    }

    @Test
    fun given_hidden_password_when_reveal_toggled_then_password_becomes_visible_and_can_be_hidden_again() {
        composeTestRule.setContent {
            LoginScreen(
                uiState = LoginUiState.Idle,
                onLogin = {},
                navigationEvent = emptyFlow(),
                onLoginSuccess = {}
            )
        }
        composeTestRule.onNodeWithContentDescription("Mostrar contraseña").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reveal_toggle").performClick()
        composeTestRule.onNodeWithContentDescription("Ocultar contraseña").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reveal_toggle").performClick()
        composeTestRule.onNodeWithContentDescription("Mostrar contraseña").assertIsDisplayed()
    }

    @Test
    fun given_loading_state_when_displayed_then_shows_loading_indicator_and_disables_submit() {
        composeTestRule.setContent {
            LoginScreen(
                uiState = LoginUiState.Loading,
                onLogin = {},
                navigationEvent = emptyFlow(),
                onLoginSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_button").assertIsNotEnabled()
    }

    @Test
    fun given_validation_error_when_displayed_then_shows_error_next_to_password_field() {
        composeTestRule.setContent {
            LoginScreen(
                uiState = LoginUiState.ValidationError(passwordError = "Ingrese su contraseña"),
                onLogin = {},
                navigationEvent = emptyFlow(),
                onLoginSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("password_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ingrese su contraseña").assertIsDisplayed()
    }

    @Test
    fun given_error_state_when_displayed_then_shows_general_error_message() {
        composeTestRule.setContent {
            LoginScreen(
                uiState = LoginUiState.Error(message = "Contraseña incorrecta"),
                onLogin = {},
                navigationEvent = emptyFlow(),
                onLoginSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("error_message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contraseña incorrecta").assertIsDisplayed()
    }

    @Test
    fun given_a_password_typed_when_login_clicked_then_onLogin_receives_the_password() {
        var capturedPassword = ""
        composeTestRule.setContent {
            LoginScreen(
                uiState = LoginUiState.Idle,
                onLogin = { capturedPassword = it },
                navigationEvent = emptyFlow(),
                onLoginSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("password_field").performTextInput("mySecurePassword1")
        composeTestRule.onNodeWithTag("login_button").performClick()
        assertEquals("mySecurePassword1", capturedPassword)
    }

    @Test
    fun given_navigation_event_when_received_then_calls_onLoginSuccess() {
        val channel = Channel<NavigationTarget>(Channel.BUFFERED)
        var callbackInvoked = false
        composeTestRule.setContent {
            LoginScreen(
                uiState = LoginUiState.Idle,
                onLogin = {},
                navigationEvent = channel.receiveAsFlow(),
                onLoginSuccess = { callbackInvoked = true }
            )
        }
        channel.trySend(NavigationTarget.Home)
        composeTestRule.waitUntil(timeoutMillis = 3000) { callbackInvoked }
        assertTrue(callbackInvoked)
    }
}
