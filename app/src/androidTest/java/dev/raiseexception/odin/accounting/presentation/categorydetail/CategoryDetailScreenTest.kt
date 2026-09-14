package dev.raiseexception.odin.accounting.presentation.categorydetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import org.junit.Rule
import org.junit.Test

class CategoryDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_content_state_when_displayed_then_shows_all_category_data() {
        composeTestRule.setContent {
            CategoryDetailScreen(
                uiState = CategoryDetailUiState.Content(
                    name = "Alimentación",
                    type = CategoryType.EXPENSE,
                    description = "Comida y mercado",
                    color = "#E57373",
                    formattedCreatedAt = "14 de septiembre de 2026"
                ),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithText("Alimentación").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gasto").assertIsDisplayed()
        composeTestRule.onNodeWithText("Comida y mercado").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_dot").assertIsDisplayed()
        composeTestRule.onNodeWithText("14 de septiembre de 2026").assertIsDisplayed()
    }

    @Test
    fun given_content_without_description_when_displayed_then_hides_description() {
        composeTestRule.setContent {
            CategoryDetailScreen(
                uiState = CategoryDetailUiState.Content(
                    name = "Salario",
                    type = CategoryType.INCOME,
                    description = "",
                    color = "#4CAF50",
                    formattedCreatedAt = "15 de enero de 2026"
                ),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithText("Salario").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ingreso").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_text").assertDoesNotExist()
    }

    @Test
    fun given_not_found_state_when_displayed_then_shows_error_message() {
        composeTestRule.setContent {
            CategoryDetailScreen(
                uiState = CategoryDetailUiState.NotFound,
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithText("Categoría no encontrada").assertIsDisplayed()
    }

    @Test
    fun given_error_state_when_displayed_then_shows_error_message() {
        composeTestRule.setContent {
            CategoryDetailScreen(
                uiState = CategoryDetailUiState.Error(message = "Error al acceder a los datos"),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithText("Error al acceder a los datos").assertIsDisplayed()
    }

    @Test
    fun given_loading_state_when_displayed_then_shows_loading_indicator() {
        composeTestRule.setContent {
            CategoryDetailScreen(
                uiState = CategoryDetailUiState.Loading,
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }
}
