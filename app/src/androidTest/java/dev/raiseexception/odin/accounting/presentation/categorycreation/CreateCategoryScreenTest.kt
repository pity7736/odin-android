package dev.raiseexception.odin.accounting.presentation.categorycreation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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

class CreateCategoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_idle_state_when_displayed_then_shows_name_field_type_picker_description_field_and_create_action() {
        composeTestRule.setContent {
            CreateCategoryScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateCategoryUiState.Idle,
                onCreate = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_picker").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_button").assertIsDisplayed()
    }

    @Test
    fun given_validation_error_state_when_displayed_then_shows_field_errors_inline() {
        composeTestRule.setContent {
            CreateCategoryScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateCategoryUiState.ValidationError(
                    nameError = "El nombre es obligatorio.",
                    typeError = "El tipo de categoría es obligatorio.",
                    descriptionError = null,
                    colorError = null
                ),
                onCreate = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("name_field_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_field_error").assertIsDisplayed()
    }

    @Test
    fun given_loading_state_when_displayed_then_create_action_is_disabled() {
        composeTestRule.setContent {
            CreateCategoryScreen(
                unexpectedFailureEvent = emptyFlow(),
                uiState = CreateCategoryUiState.Loading,
                onCreate = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {}
            )
        }
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun given_typed_text_and_an_unexpected_failure_when_accepting_the_message_then_the_typed_text_is_kept() {
        val unexpectedFailures = Channel<Unit>(Channel.BUFFERED)
        composeTestRule.setContent {
            CreateCategoryScreen(
                unexpectedFailureEvent = unexpectedFailures.receiveAsFlow(),
                uiState = CreateCategoryUiState.Idle,
                onCreate = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onCreateSuccess = {},
            )
        }
        composeTestRule.onNodeWithTag("name_field").performTextInput("Mercado")
        unexpectedFailures.trySend(Unit)
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("name_field").assertExists()
        composeTestRule.onNodeWithText("Aceptar").performClick()
        composeTestRule.onNodeWithText("Ocurrió un error inesperado.").assertDoesNotExist()
        composeTestRule.onNodeWithTag("name_field").assertTextContains("Mercado", substring = true)
    }
}
