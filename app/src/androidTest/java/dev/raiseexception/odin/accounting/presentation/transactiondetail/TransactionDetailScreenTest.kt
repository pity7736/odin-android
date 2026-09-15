package dev.raiseexception.odin.accounting.presentation.transactiondetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.raiseexception.odin.ui.theme.IncomeGreen
import dev.raiseexception.odin.ui.theme.ExpenseRed
import org.junit.Rule
import org.junit.Test

class TransactionDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_content_state_with_income_when_displayed_then_shows_income_details() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                uiState = TransactionDetailUiState.Content(
                    formattedAmount = "+$1.000,00",
                    amountColor = IncomeGreen,
                    formattedDate = "14 de septiembre de 2026",
                    categoryName = "Salario",
                    accountName = "Ahorros",
                    description = "Pago mensual",
                    isIncome = true,
                ),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
            )
        }
        composeTestRule.onNodeWithText("+$1.000,00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ingreso").assertIsDisplayed()
        composeTestRule.onNodeWithText("14 de septiembre de 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Salario").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ahorros").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pago mensual").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_icon").assertIsDisplayed()
    }

    @Test
    fun given_content_state_with_expense_when_displayed_then_shows_expense_details() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                uiState = TransactionDetailUiState.Content(
                    formattedAmount = "-$500,00",
                    amountColor = ExpenseRed,
                    formattedDate = "15 de septiembre de 2026",
                    categoryName = "Alimentación",
                    accountName = "Efectivo",
                    description = "Mercado semanal",
                    isIncome = false,
                ),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
            )
        }
        composeTestRule.onNodeWithText("-$500,00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gasto").assertIsDisplayed()
        composeTestRule.onNodeWithText("15 de septiembre de 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alimentación").assertIsDisplayed()
        composeTestRule.onNodeWithText("Efectivo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mercado semanal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_icon").assertIsDisplayed()
    }

    @Test
    fun given_content_state_with_empty_description_when_displayed_then_hides_description() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                uiState = TransactionDetailUiState.Content(
                    formattedAmount = "+$1.000,00",
                    amountColor = IncomeGreen,
                    formattedDate = "14 de septiembre de 2026",
                    categoryName = "Salario",
                    accountName = "Ahorros",
                    description = "",
                    isIncome = true,
                ),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
            )
        }
        composeTestRule.onNodeWithText("+$1.000,00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_text").assertDoesNotExist()
    }

    @Test
    fun given_loading_state_when_displayed_then_shows_loading_indicator() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                uiState = TransactionDetailUiState.Loading,
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
            )
        }
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun given_not_found_state_when_displayed_then_shows_not_found_message() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                uiState = TransactionDetailUiState.NotFound,
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
            )
        }
        composeTestRule.onNodeWithText("Transacción no encontrada").assertIsDisplayed()
    }

    @Test
    fun given_error_state_when_displayed_then_shows_error_message() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                uiState = TransactionDetailUiState.Error(message = "Error al acceder a los datos"),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
            )
        }
        composeTestRule.onNodeWithText("Error al acceder a los datos").assertIsDisplayed()
    }
}
