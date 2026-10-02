package dev.raiseexception.odin.accounting.presentation.expensecreation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.shared.presentation.TagSelection
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateExpenseScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val expenseCategory: Category = Category.restore(
        "expense-category-id",
        "Alimentación",
        CategoryType.EXPENSE,
        "",
        "#E57373",
        Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")
    private val nalaTag = Tag.restore("tag-nala", "Nala", "nala", createdAt)
    private val comidaTag = Tag.restore("tag-comida", "Comida", "comida", createdAt)

    @Test
    fun given_idle_state_when_displayed_then_shows_amount_date_category_and_description_fields() {
        composeTestRule.setContent {
            CreateExpenseScreen(
                uiState = CreateExpenseUiState.Idle(
                    categories = listOf(expenseCategory),
                    accountCreatedAt = LocalDate(2026, 1, 1)
                ),
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
                uiState = CreateExpenseUiState.Idle(
                    categories = listOf(expenseCategory),
                    accountCreatedAt = LocalDate(2026, 1, 1)
                ),
                onSave = { amount, _, _, _ ->
                    capturedAmount = amount
                },
                navigationEvent = emptyFlow(),
                onNavigateBack = {}
            )
        }
        composeTestRule.onNodeWithTag("amount_field").performTextInput("500,00")
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
                    accountCreatedAt = LocalDate(2026, 1, 1),
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
                    accountCreatedAt = LocalDate(2026, 1, 1),
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
                    accountCreatedAt = LocalDate(2026, 1, 1),
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

    @Test
    fun given_idle_state_when_displayed_then_shows_the_etiquetas_field_after_the_category_field() {
        setIdle(TagSelection())
        composeTestRule.onNodeWithText("Etiquetas").assertIsDisplayed()
        val categoryBounds = composeTestRule.onNodeWithTag("category_field").getUnclippedBoundsInRoot()
        val tagsBounds = composeTestRule.onNodeWithTag("tags_field").getUnclippedBoundsInRoot()
        val descriptionBounds = composeTestRule.onNodeWithTag("description_field").getUnclippedBoundsInRoot()
        assertTrue(tagsBounds.top >= categoryBounds.bottom)
        assertTrue(descriptionBounds.top >= tagsBounds.bottom)
    }

    @Test
    fun given_a_tag_selection_when_displayed_then_shows_a_chip_per_tag() {
        setIdle(TagSelection.of(listOf(nalaTag, comidaTag)))
        composeTestRule.onNodeWithTag("tag_chip_0").assertTextContains("Comida")
        composeTestRule.onNodeWithTag("tag_chip_1").assertTextContains("Nala")
    }

    @Test
    fun given_tags_in_use_when_the_etiquetas_field_is_focused_then_suggestions_are_shown() {
        setIdle(TagSelection())
        composeTestRule.onNodeWithTag("tags_field").performClick()
        composeTestRule.onNodeWithTag("tag_option_${comidaTag.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tag_option_${nalaTag.id}").assertIsDisplayed()
    }

    @Test
    fun given_a_suggestion_when_picking_it_then_on_tag_picked_receives_the_tag() {
        var pickedTag: Tag? = null
        setIdle(TagSelection(), onTagPicked = { pickedTag = it })
        composeTestRule.onNodeWithTag("tags_field").performClick()
        composeTestRule.onNodeWithTag("tag_option_${nalaTag.id}").performClick()
        assertEquals(nalaTag, pickedTag)
    }

    @Test
    fun given_chips_when_tapping_the_x_of_the_second_then_on_tag_removed_receives_its_index() {
        var removedIndex = -1
        setIdle(TagSelection.of(listOf(nalaTag, comidaTag)), onTagRemoved = { removedIndex = it })
        composeTestRule.onNodeWithTag("tag_remove_1").performClick()
        assertEquals(1, removedIndex)
    }

    @Test
    fun given_chips_when_tapping_a_chip_name_then_no_tag_is_removed() {
        var removedIndex = -1
        setIdle(TagSelection.of(listOf(nalaTag, comidaTag)), onTagRemoved = { removedIndex = it })
        composeTestRule.onNodeWithTag("tag_chip_1").performClick()
        assertEquals(-1, removedIndex)
    }

    @Test
    fun given_typed_text_when_confirming_the_keyboard_then_on_add_tag_is_called() {
        var addCount = 0
        setIdle(TagSelection(text = "Carro"), onAddTag = { addCount++ })
        composeTestRule.onNodeWithTag("tags_field").performImeAction()
        assertEquals(1, addCount)
    }

    @Test
    fun given_typing_in_the_etiquetas_field_when_entering_text_then_on_tag_text_change_receives_it() {
        var typedText = ""
        setIdle(TagSelection(), onTagTextChange = { typedText = it })
        composeTestRule.onNodeWithTag("tags_field").performTextInput("Carro")
        assertEquals("Carro", typedText)
    }

    @Test
    fun given_a_tags_error_when_displayed_then_it_is_shown_next_to_the_etiquetas_field() {
        setIdle(TagSelection(error = "La etiqueta no puede superar 30 caracteres."))
        composeTestRule.onNodeWithTag("tags_field_error")
            .assertTextContains("La etiqueta no puede superar 30 caracteres.")
    }

    @Test
    fun given_five_tags_when_displayed_then_the_etiquetas_input_is_disabled_without_an_error() {
        val fiveTags = (1..5).map { Tag.restore("tag-$it", "Etiqueta $it", "etiqueta $it", createdAt) }
        setIdle(TagSelection.of(fiveTags))
        composeTestRule.onNodeWithTag("tags_field").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("tags_field_error").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tags_field_hint").assertTextContains("Máximo 5 etiquetas por gasto.")
    }

    @Test
    fun given_no_tags_when_displayed_then_the_limit_guidance_is_shown() {
        setIdle(TagSelection())
        composeTestRule.onNodeWithTag("tags_field_hint").assertTextContains("Máximo 5 etiquetas por gasto.")
    }

    @Test
    fun given_a_tags_error_when_displayed_then_it_replaces_the_limit_guidance() {
        setIdle(TagSelection(error = "La etiqueta no puede superar 30 caracteres."))
        composeTestRule.onNodeWithTag("tags_field_hint").assertDoesNotExist()
    }

    @Test
    fun given_idle_with_tags_when_the_state_becomes_saving_then_the_chips_stay_visible() {
        var uiState: CreateExpenseUiState by mutableStateOf(
            CreateExpenseUiState.Idle(
                categories = listOf(expenseCategory),
                accountCreatedAt = LocalDate(2026, 1, 1),
                tags = listOf(nalaTag, comidaTag),
                tagSelection = TagSelection.of(listOf(nalaTag))
            )
        )
        composeTestRule.setContent {
            CreateExpenseScreen(
                uiState = uiState,
                onSave = { _, _, _, _ -> },
                navigationEvent = emptyFlow(),
                onNavigateBack = {}
            )
        }
        uiState = CreateExpenseUiState.Saving
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tag_chip_0").assertTextContains("Nala")
    }

    private fun setIdle(
        tagSelection: TagSelection,
        onTagTextChange: (String) -> Unit = {},
        onAddTag: () -> Unit = {},
        onTagPicked: (Tag) -> Unit = {},
        onTagRemoved: (Int) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CreateExpenseScreen(
                uiState = CreateExpenseUiState.Idle(
                    categories = listOf(expenseCategory),
                    accountCreatedAt = LocalDate(2026, 1, 1),
                    tags = listOf(nalaTag, comidaTag),
                    tagSelection = tagSelection
                ),
                onSave = { _, _, _, _ -> },
                onTagTextChange = onTagTextChange,
                onAddTag = onAddTag,
                onTagPicked = onTagPicked,
                onTagRemoved = onTagRemoved,
                navigationEvent = emptyFlow(),
                onNavigateBack = {}
            )
        }
    }
}
