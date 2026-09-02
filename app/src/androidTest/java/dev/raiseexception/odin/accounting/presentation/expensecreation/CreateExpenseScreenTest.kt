package dev.raiseexception.odin.accounting.presentation.expensecreation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.testutil.CategoryBuilder
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test

class CreateExpenseScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val expenseCategory: Category = CategoryBuilder()
        .name("Alimentación")
        .type(CategoryType.EXPENSE)
        .build()

    @Test
    fun given_idle_state_when_displayed_then_shows_amount_date_category_and_description_fields() {
        composeTestRule.setContent {
            CreateExpenseScreen(
                uiState = CreateExpenseUiState.Idle(categories = listOf(expenseCategory)),
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithTag("amount_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_button").assertIsDisplayed()
    }

    @Test
    fun given_valid_input_when_save_tapped_then_expense_is_submitted() {
        var capturedAmount = ""
        composeTestRule.setContent {
            CreateExpenseScreen(
                uiState = CreateExpenseUiState.Idle(categories = listOf(expenseCategory)),
                onSave = { amount, _, _, _ ->
                    capturedAmount = amount
                },
                navigationEvent = emptyFlow(),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithTag("amount_field").performTextInput("500.00")
        composeTestRule.onNodeWithTag("category_field").performClick()
        composeTestRule.onNodeWithTag("category_option_${expenseCategory.id}").performClick()
        composeTestRule.onNodeWithTag("save_button").performClick()
        org.junit.Assert.assertEquals("500.00", capturedAmount)
    }

    @Test
    fun given_invalid_amount_when_save_tapped_then_amount_error_is_shown() {
        composeTestRule.setContent {
            CreateExpenseScreen(
                uiState = CreateExpenseUiState.ValidationError(
                    categories = listOf(expenseCategory),
                    amountError = "El monto debe ser mayor que cero.",
                    dateError = null,
                    categoryError = null
                ),
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithTag("amount_field_error").assertIsDisplayed()
    }

    @Test
    fun given_future_date_when_save_tapped_then_date_error_is_shown() {
        composeTestRule.setContent {
            CreateExpenseScreen(
                uiState = CreateExpenseUiState.ValidationError(
                    categories = listOf(expenseCategory),
                    amountError = null,
                    dateError = "La fecha debe ser hoy o en el pasado.",
                    categoryError = null
                ),
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithTag("date_field_error").assertIsDisplayed()
    }

    @Test
    fun given_missing_category_when_save_tapped_then_category_error_is_shown() {
        composeTestRule.setContent {
            CreateExpenseScreen(
                uiState = CreateExpenseUiState.ValidationError(
                    categories = listOf(expenseCategory),
                    amountError = null,
                    dateError = null,
                    categoryError = "La categoría es obligatoria."
                ),
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithTag("category_field_error").assertIsDisplayed()
    }
}
