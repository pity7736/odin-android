package dev.raiseexception.odin.accounting.presentation.incomecreation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.testutil.CategoryBuilder
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class CreateIncomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val incomeCategory: Category = CategoryBuilder()
        .name("Salario")
        .type(CategoryType.INCOME)
        .build()

    @Test
    fun given_idle_state_when_displayed_then_shows_amount_date_category_and_description_fields() {
        composeTestRule.setContent {
            CreateIncomeScreen(
                uiState = CreateIncomeUiState.Idle(categories = listOf(incomeCategory)),
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
    fun given_valid_input_when_save_tapped_then_income_is_submitted() {
        var capturedAmount = ""
        var capturedDate: LocalDate? = null
        var capturedCategoryId = ""
        var capturedDescription = ""
        composeTestRule.setContent {
            CreateIncomeScreen(
                uiState = CreateIncomeUiState.Idle(categories = listOf(incomeCategory)),
                onSave = { amount, date, categoryId, description ->
                    capturedAmount = amount
                    capturedDate = date
                    capturedCategoryId = categoryId
                    capturedDescription = description
                },
                navigationEvent = emptyFlow(),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithTag("amount_field").performTextInput("500.00")
        composeTestRule.onNodeWithTag("date_field").performTextInput("2026-08-29")
        composeTestRule.onNodeWithTag("category_field").performClick()
        composeTestRule.onNodeWithTag("category_option_${incomeCategory.id}").performClick()
        composeTestRule.onNodeWithTag("save_button").performClick()
        assertEquals("500.00", capturedAmount)
        assertEquals(LocalDate(2026, 8, 29), capturedDate)
        assertEquals(incomeCategory.id, capturedCategoryId)
        assertEquals("", capturedDescription)
    }

    @Test
    fun given_invalid_amount_when_save_tapped_then_amount_error_is_shown() {
        composeTestRule.setContent {
            CreateIncomeScreen(
                uiState = CreateIncomeUiState.ValidationError(
                    categories = listOf(incomeCategory),
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
            CreateIncomeScreen(
                uiState = CreateIncomeUiState.ValidationError(
                    categories = listOf(incomeCategory),
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
            CreateIncomeScreen(
                uiState = CreateIncomeUiState.ValidationError(
                    categories = listOf(incomeCategory),
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
