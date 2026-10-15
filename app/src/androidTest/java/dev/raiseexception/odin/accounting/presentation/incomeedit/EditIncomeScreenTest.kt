package dev.raiseexception.odin.accounting.presentation.incomeedit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditIncomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val salaryCategory: Category = Category.restore(
        "cat-salary",
        "Salario",
        CategoryType.INCOME,
        "",
        "#4CAF50",
        Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val bonusCategory: Category = Category.restore(
        "cat-bonus",
        "Bonos",
        CategoryType.INCOME,
        "",
        "#2196F3",
        Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val editing = EditIncomeUiState.Editing(
        amount = "100000.50",
        date = "2026-03-05",
        categoryId = "cat-salary",
        categoryName = "Salario",
        description = "Pago mensual",
        accountName = "Ahorros",
        accountCreatedAt = LocalDate(2026, 3, 1),
        categories = listOf(salaryCategory, bonusCategory),
    )

    @Test
    fun given_editing_when_shown_then_the_title_and_fields_are_pre_filled_with_the_formatted_amount_date_category_and_description() {
        setScreen(editing)
        composeTestRule.onNodeWithTag("edit_income_title").assertTextContains("Editar ingreso")
        composeTestRule.onNodeWithText("100.000,50").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_field").assertTextContains("2026-03-05")
        composeTestRule.onNodeWithTag("category_field").assertTextContains("Salario")
        composeTestRule.onNodeWithTag("description_field").assertTextContains("Pago mensual")
    }

    @Test
    fun given_editing_when_shown_then_the_account_name_is_displayed_read_only() {
        setScreen(editing)
        composeTestRule.onNodeWithTag("account_readonly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_readonly").assertTextContains("Ahorros")
        composeTestRule.onNodeWithTag("account_readonly")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
    }

    @Test
    fun given_editing_when_changing_the_fields_and_saving_then_on_save_receives_the_raw_values_and_an_existing_or_new_category_input() {
        val savedValues = mutableListOf<List<Any>>()
        setScreen(editing, onSave = { amount, date, category, description ->
            savedValues += listOf(amount, date, category, description)
        })
        composeTestRule.onNodeWithTag("amount_field").performTextClearance()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("150000,5")
        composeTestRule.onNodeWithTag("category_field").performTextClearance()
        composeTestRule.onNodeWithTag("category_field").performTextInput("Bonos")
        composeTestRule.onNodeWithTag("description_field").performTextClearance()
        composeTestRule.onNodeWithTag("description_field").performTextInput("Bono")
        composeTestRule.onNodeWithTag("save_button").performClick()
        composeTestRule.onNodeWithTag("category_field").performTextClearance()
        composeTestRule.onNodeWithTag("category_field").performTextInput("Arriendos")
        composeTestRule.onNodeWithTag("save_button").performClick()
        assertEquals(
            listOf("150000.5", "2026-03-05", CategoryInput.Existing("cat-bonus"), "Bono"),
            savedValues[0]
        )
        assertEquals(listOf("150000.5", "2026-03-05", CategoryInput.New("Arriendos"), "Bono"), savedValues[1])
    }

    @Test
    fun given_editing_without_changes_when_saving_then_on_save_receives_the_original_values_and_the_original_category() {
        val savedValues = mutableListOf<List<Any>>()
        setScreen(editing, onSave = { amount, date, category, description ->
            savedValues += listOf(amount, date, category, description)
        })
        composeTestRule.onNodeWithTag("save_button").performClick()
        assertEquals(
            listOf(listOf("100000.50", "2026-03-05", CategoryInput.Existing("cat-salary"), "Pago mensual")),
            savedValues
        )
    }

    @Test
    fun given_editing_when_clearing_the_description_and_saving_then_on_save_receives_an_empty_description() {
        var savedDescription: String? = null
        setScreen(editing, onSave = { _, _, _, description -> savedDescription = description })
        composeTestRule.onNodeWithTag("description_field").performTextClearance()
        composeTestRule.onNodeWithTag("save_button").performClick()
        assertEquals("", savedDescription)
    }

    @Test
    fun given_field_errors_when_shown_then_each_error_appears_next_to_its_field_and_typed_values_are_kept() {
        var uiState: EditIncomeUiState by mutableStateOf(editing)
        composeTestRule.setContent {
            EditIncomeScreen(
                uiState = uiState,
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                onSaved = {},
                onCancel = {},
            )
        }
        composeTestRule.onNodeWithTag("amount_field").performTextClearance()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("79999")
        composeTestRule.onNodeWithTag("description_field").performTextClearance()
        composeTestRule.onNodeWithTag("description_field").performTextInput("Bono")
        uiState = editing.copy(
            amountError = "El saldo de la cuenta no puede quedar negativo.",
            dateError = "El saldo de la cuenta quedaría negativo el 8 de marzo.",
            categoryError = "La categoría es obligatoria.",
            descriptionError = "La descripción no puede superar los 500 caracteres.",
        )
        composeTestRule.onNodeWithTag("amount_field_error")
            .assertTextContains("El saldo de la cuenta no puede quedar negativo.")
        composeTestRule.onNodeWithTag("date_field_error")
            .assertTextContains("El saldo de la cuenta quedaría negativo el 8 de marzo.")
        composeTestRule.onNodeWithTag("category_field_error").assertTextContains("La categoría es obligatoria.")
        composeTestRule.onNodeWithTag("description_field_error")
            .assertTextContains("La descripción no puede superar los 500 caracteres.")
        composeTestRule.onNodeWithText("79.999").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field").assertTextContains("Bono")
    }

    @Test
    fun given_save_error_when_shown_then_the_message_is_displayed_and_typed_values_are_kept() {
        var uiState: EditIncomeUiState by mutableStateOf(editing)
        composeTestRule.setContent {
            EditIncomeScreen(
                uiState = uiState,
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                onSaved = {},
                onCancel = {},
            )
        }
        composeTestRule.onNodeWithTag("amount_field").performTextClearance()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("150000")
        uiState = editing.copy(saveError = "No se pudo editar el ingreso. Inténtalo de nuevo.")
        composeTestRule.onNodeWithTag("save_error")
            .assertTextContains("No se pudo editar el ingreso. Inténtalo de nuevo.")
        composeTestRule.onNodeWithText("150.000").assertIsDisplayed()
    }

    @Test
    fun given_is_saving_when_shown_then_the_save_action_is_disabled() {
        setScreen(editing.copy(isSaving = true))
        composeTestRule.onNodeWithTag("save_button").assertIsNotEnabled()
    }

    @Test
    fun given_editing_when_cancelling_then_on_cancel_is_called() {
        var cancelCount = 0
        setScreen(editing, onCancel = { cancelCount++ })
        composeTestRule.onNodeWithTag("cancel_button").performClick()
        assertEquals(1, cancelCount)
    }

    @Test
    fun given_not_found_when_shown_then_transaccion_no_encontrada_is_displayed() {
        setScreen(EditIncomeUiState.NotFound)
        composeTestRule.onNodeWithText("Transacción no encontrada").assertIsDisplayed()
    }

    @Test
    fun given_an_unexpected_failure_when_displayed_then_shows_the_retry_content_and_no_information() {
        setScreen(EditIncomeUiState.UnexpectedFailure)
        composeTestRule.onNodeWithTag("unexpected_failure_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unexpected_failure_retry").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_field").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_button").assertDoesNotExist()
    }

    private fun setScreen(
        uiState: EditIncomeUiState,
        onSave: (String, String, CategoryInput, String) -> Unit = { _, _, _, _ -> },
        onCancel: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            EditIncomeScreen(
                uiState = uiState,
                onSave = onSave,
                navigationEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                onRetry = {},
                onSaved = {},
                onCancel = onCancel,
            )
        }
    }
}
