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
    fun `given idle state, when displayed, then shows password field and submit hidden by default`() {
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
    fun `given hidden password, when reveal toggled, then password becomes visible and can be hidden again`() {
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
    fun `given loading state, when displayed, then shows loading indicator and disables submit`() {
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
    fun `given validation error, when displayed, then shows error next to password field`() {
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
    fun `given error state, when displayed, then shows general error message`() {
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
    fun `given a password typed, when login clicked, then onLogin receives the password`() {
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
    fun `given navigation event, when received, then calls onLoginSuccess`() {
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
