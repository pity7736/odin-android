package dev.raiseexception.odin.accounting.presentation.expenseedit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
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

class EditExpenseScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val foodCategory: Category = Category.restore(
        "cat-food",
        "Alimentación",
        CategoryType.EXPENSE,
        "",
        "#E57373",
        Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val transportCategory: Category = Category.restore(
        "cat-transport",
        "Transporte",
        CategoryType.EXPENSE,
        "",
        "#2196F3",
        Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val editing = EditExpenseUiState.Editing(
        amount = "30000.50",
        date = "2026-03-10",
        categoryId = "cat-food",
        categoryName = "Alimentación",
        description = "Mercado",
        accountName = "Ahorros",
        accountCreatedAt = LocalDate(2026, 3, 1),
        categories = listOf(foodCategory, transportCategory),
    )

    @Test
    fun given_editing_when_shown_then_fields_are_pre_filled_with_the_amount_formatted_date_category_and_description() {
        setScreen(editing)
        composeTestRule.onNodeWithText("30.000,50").assertIsDisplayed()
        composeTestRule.onNodeWithTag("date_field").assertTextContains("2026-03-10")
        composeTestRule.onNodeWithTag("category_field").assertTextContains("Alimentación")
        composeTestRule.onNodeWithTag("description_field").assertTextContains("Mercado")
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
        composeTestRule.onNodeWithTag("amount_field").performTextInput("45000,5")
        composeTestRule.onNodeWithTag("category_field").performTextClearance()
        composeTestRule.onNodeWithTag("category_field").performTextInput("Transporte")
        composeTestRule.onNodeWithTag("description_field").performTextClearance()
        composeTestRule.onNodeWithTag("description_field").performTextInput("Bus")
        composeTestRule.onNodeWithTag("save_button").performClick()
        composeTestRule.onNodeWithTag("category_field").performTextClearance()
        composeTestRule.onNodeWithTag("category_field").performTextInput("Viajes")
        composeTestRule.onNodeWithTag("save_button").performClick()
        assertEquals(
            listOf("45000.5", "2026-03-10", CategoryInput.Existing("cat-transport"), "Bus"),
            savedValues[0]
        )
        assertEquals(listOf("45000.5", "2026-03-10", CategoryInput.New("Viajes"), "Bus"), savedValues[1])
    }

    @Test
    fun given_editing_without_changes_when_saving_then_on_save_receives_the_original_values_and_existing_with_the_original_category_id() {
        val savedValues = mutableListOf<List<Any>>()
        setScreen(editing, onSave = { amount, date, category, description ->
            savedValues += listOf(amount, date, category, description)
        })
        composeTestRule.onNodeWithTag("save_button").performClick()
        assertEquals(
            listOf(listOf("30000.50", "2026-03-10", CategoryInput.Existing("cat-food"), "Mercado")),
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
        var uiState: EditExpenseUiState by mutableStateOf(editing)
        composeTestRule.setContent {
            EditExpenseScreen(
                uiState = uiState,
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {},
                onCancel = {},
            )
        }
        composeTestRule.onNodeWithTag("amount_field").performTextClearance()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("999999")
        composeTestRule.onNodeWithTag("description_field").performTextClearance()
        composeTestRule.onNodeWithTag("description_field").performTextInput("Cena")
        uiState = editing.copy(
            amountError = "El monto supera el saldo disponible.",
            dateError = "La fecha debe ser hoy o en el pasado.",
            categoryError = "La categoría es obligatoria.",
            descriptionError = "La descripción no puede superar los 500 caracteres.",
        )
        composeTestRule.onNodeWithTag("amount_field_error").assertTextContains("El monto supera el saldo disponible.")
        composeTestRule.onNodeWithTag("date_field_error").assertTextContains("La fecha debe ser hoy o en el pasado.")
        composeTestRule.onNodeWithTag("category_field_error").assertTextContains("La categoría es obligatoria.")
        composeTestRule.onNodeWithTag("description_field_error")
            .assertTextContains("La descripción no puede superar los 500 caracteres.")
        composeTestRule.onNodeWithText("999.999").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_field").assertTextContains("Cena")
    }

    @Test
    fun given_save_error_when_shown_then_the_message_is_displayed_and_typed_values_are_kept() {
        var uiState: EditExpenseUiState by mutableStateOf(editing)
        composeTestRule.setContent {
            EditExpenseScreen(
                uiState = uiState,
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onSaved = {},
                onCancel = {},
            )
        }
        composeTestRule.onNodeWithTag("amount_field").performTextClearance()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("45000")
        uiState = editing.copy(saveError = "No se pudo editar el gasto. Inténtalo de nuevo.")
        composeTestRule.onNodeWithTag("save_error").assertTextContains("No se pudo editar el gasto. Inténtalo de nuevo.")
        composeTestRule.onNodeWithText("45.000").assertIsDisplayed()
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
        setScreen(EditExpenseUiState.NotFound)
        composeTestRule.onNodeWithText("Transacción no encontrada").assertIsDisplayed()
    }

    @Test
    fun given_editing_with_an_expense_date_when_opening_the_date_picker_then_the_expense_date_is_selected() {
        setScreen(editing)
        composeTestRule.onNodeWithTag("date_field").performClick()
        composeTestRule.onNode(hasText("10", substring = true) and isSelected() and hasClickAction())
            .assertIsDisplayed()
    }

    private fun setScreen(
        uiState: EditExpenseUiState,
        onSave: (String, String, CategoryInput, String) -> Unit = { _, _, _, _ -> },
        onCancel: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            EditExpenseScreen(
                uiState = uiState,
                onSave = onSave,
                navigationEvent = emptyFlow(),
                onSaved = {},
                onCancel = onCancel,
            )
        }
    }
}
