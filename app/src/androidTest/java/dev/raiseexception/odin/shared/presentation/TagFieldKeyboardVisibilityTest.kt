package dev.raiseexception.odin.shared.presentation

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.presentation.expensecreation.CreateExpenseScreen
import dev.raiseexception.odin.accounting.presentation.expensecreation.CreateExpenseUiState
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TagFieldKeyboardVisibilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val nalaTag = Tag.restore("tag-nala", "Nala", "nala", Instant.parse("2026-01-01T00:00:00Z"))

    @Test
    fun given_the_keyboard_is_open_on_the_etiquetas_field_when_a_new_tag_is_added_then_the_field_and_its_chip_are_above_the_keyboard() {
        this.setForm(existingTags = emptyList())
        this.focusTagsField()
        composeTestRule.onNodeWithTag(TAGS_FIELD_TAG).performTextInput("Carro")
        composeTestRule.onNodeWithTag(TAGS_FIELD_TAG).performImeAction()
        this.assertAboveOpenKeyboard(TAGS_FIELD_TAG)
        this.assertAboveOpenKeyboard(FIRST_CHIP_TAG)
    }

    @Test
    fun given_the_keyboard_is_open_on_the_etiquetas_field_when_a_suggestion_is_picked_then_the_field_and_its_chip_are_above_the_keyboard() {
        this.setForm(existingTags = listOf(this.nalaTag))
        this.focusTagsField()
        composeTestRule.onNodeWithTag("tag_option_${this.nalaTag.id}").performClick()
        this.assertAboveOpenKeyboard(TAGS_FIELD_TAG)
        this.assertAboveOpenKeyboard(FIRST_CHIP_TAG)
    }

    private fun setForm(existingTags: List<Tag>) {
        val savingsAccount = (Account.create("Ahorros", "100000", Currency.COP, MoneyAccountKind.SAVINGS, "") as Outcome.Success)
            .value
        composeTestRule.runOnUiThread { composeTestRule.activity.enableEdgeToEdge() }
        composeTestRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    CreateExpenseUiState.Idle(categories = emptyList(), accounts = listOf(savingsAccount), tags = existingTags)
                )
            }
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).imePadding()) {
                    CreateExpenseScreen(
                        uiState = uiState,
                        onSave = { _, _, _, _ -> },
                        onTagTextChange = { text ->
                            uiState = uiState.copy(tagSelection = uiState.tagSelection.withText(text))
                        },
                        onAddTag = {
                            uiState = uiState.copy(tagSelection = uiState.tagSelection.withTextAdded(uiState.tags))
                        },
                        onTagPicked = { tag ->
                            uiState = uiState.copy(tagSelection = uiState.tagSelection.withTagPicked(tag))
                        },
                        navigationEvent = emptyFlow(),
                        onNavigateBack = {},
                    )
                }
            }
        }
    }

    private fun focusTagsField() {
        composeTestRule.onNodeWithTag(TAGS_FIELD_TAG).performScrollTo().performClick()
        composeTestRule.waitUntil(KEYBOARD_TIMEOUT_MILLIS) { this.keyboardHeightInWindow() > 0 }
        composeTestRule.waitForIdle()
    }

    private fun assertAboveOpenKeyboard(nodeTag: String) {
        composeTestRule.waitForIdle()
        val nodeBottom = composeTestRule.onNodeWithTag(nodeTag).fetchSemanticsNode().boundsInWindow.bottom
        val keyboardHeight = this.keyboardHeightInWindow()
        val keyboardTop = this.keyboardTopInWindow()
        assertTrue("The keyboard must still be open", keyboardHeight > 0)
        assertTrue(
            "Node $nodeTag bottom ($nodeBottom) must be above the keyboard top ($keyboardTop)",
            nodeBottom < keyboardTop
        )
    }

    private fun keyboardTopInWindow(): Float = composeTestRule.runOnUiThread {
        val decorView = composeTestRule.activity.window.decorView
        (decorView.height - this.imeInsetBottom(decorView)).toFloat()
    }

    private fun keyboardHeightInWindow(): Int = composeTestRule.runOnUiThread {
        this.imeInsetBottom(composeTestRule.activity.window.decorView)
    }

    private fun imeInsetBottom(decorView: android.view.View): Int =
        ViewCompat.getRootWindowInsets(decorView)
            ?.getInsets(WindowInsetsCompat.Type.ime())
            ?.bottom
            ?: 0

    companion object {
        private const val TAGS_FIELD_TAG = "tags_field"
        private const val FIRST_CHIP_TAG = "tag_chip_0"
        private const val KEYBOARD_TIMEOUT_MILLIS = 10_000L
    }
}
