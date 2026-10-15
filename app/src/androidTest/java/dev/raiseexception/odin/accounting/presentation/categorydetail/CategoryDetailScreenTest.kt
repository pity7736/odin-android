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
                onRetry = {},
                uiState = CategoryDetailUiState.Content(
                    name = "Alimentación",
                    type = CategoryType.EXPENSE,
                    description = "Comida y mercado",
                    color = "#E57373",
                    formattedCreatedAt = "14 de septiembre de 2026"
                ),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
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
                onRetry = {},
                uiState = CategoryDetailUiState.Content(
                    name = "Salario",
                    type = CategoryType.INCOME,
                    description = "",
                    color = "#4CAF50",
                    formattedCreatedAt = "15 de enero de 2026"
                ),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
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
                onRetry = {},
                uiState = CategoryDetailUiState.NotFound,
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
            )
        }
        composeTestRule.onNodeWithText("Categoría no encontrada").assertIsDisplayed()
    }

    @Test
    fun given_error_state_when_displayed_then_shows_error_message() {
        composeTestRule.setContent {
            CategoryDetailScreen(
                onRetry = {},
                uiState = CategoryDetailUiState.Error(message = "Error al acceder a los datos"),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
            )
        }
        composeTestRule.onNodeWithText("Error al acceder a los datos").assertIsDisplayed()
    }

    @Test
    fun given_loading_state_when_displayed_then_shows_loading_indicator() {
        composeTestRule.setContent {
            CategoryDetailScreen(
                onRetry = {},
                uiState = CategoryDetailUiState.Loading,
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
            )
        }
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun given_an_unexpected_failure_when_displayed_then_shows_the_retry_content_and_no_information() {
        composeTestRule.setContent {
            CategoryDetailScreen(
                onRetry = {},
                uiState = CategoryDetailUiState.UnexpectedFailure,
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
            )
        }
        composeTestRule.onNodeWithTag("unexpected_failure_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unexpected_failure_retry").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_text").assertDoesNotExist()
        composeTestRule.onNodeWithTag("color_dot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("loading_indicator").assertDoesNotExist()
    }
}
