package dev.raiseexception.odin.shared.presentation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import dev.raiseexception.odin.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

class KeyboardFieldVisibilityTest {

    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val freshInstallRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement =
            object : Statement() {
                override fun evaluate() {
                    clearPersistedSalt()
                    base.evaluate()
                }
            }
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(freshInstallRule).around(composeTestRule)

    @Test
    fun given_a_form_with_a_field_near_the_bottom_when_the_field_is_focused_and_the_keyboard_opens_then_the_field_is_above_the_keyboard() {
        awaitForm()
        composeTestRule.onNodeWithTag(LOWER_FIELD_TAG).performClick()
        awaitKeyboardShown()
        assertFieldAboveKeyboard(LOWER_FIELD_TAG)
    }

    @Test
    fun given_the_keyboard_is_open_on_one_field_when_focus_moves_to_a_lower_field_then_the_lower_field_is_above_the_keyboard() {
        awaitForm()
        composeTestRule.onNodeWithTag(UPPER_FIELD_TAG).performClick()
        awaitKeyboardShown()
        composeTestRule.onNodeWithTag(LOWER_FIELD_TAG).performClick()
        composeTestRule.waitForIdle()
        assertFieldAboveKeyboard(LOWER_FIELD_TAG)
    }

    private fun assertFieldAboveKeyboard(fieldTag: String) {
        composeTestRule.waitForIdle()
        val fieldBottom = composeTestRule.onNodeWithTag(fieldTag).fetchSemanticsNode().boundsInWindow.bottom
        val keyboardTop = keyboardTopInWindow()
        assertTrue(
            "Focused field bottom ($fieldBottom) must be above the keyboard top ($keyboardTop)",
            fieldBottom < keyboardTop
        )
    }

    private fun awaitForm() {
        composeTestRule.waitUntil(FORM_TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithTag(UPPER_FIELD_TAG).fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithTag(LOWER_FIELD_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitKeyboardShown() {
        composeTestRule.waitUntil(KEYBOARD_TIMEOUT_MILLIS) { keyboardHeightInWindow() > 0 }
        composeTestRule.waitForIdle()
    }

    private fun keyboardTopInWindow(): Float = composeTestRule.runOnUiThread {
        val decorView = composeTestRule.activity.window.decorView
        (decorView.height - imeInsetBottom(decorView)).toFloat()
    }

    private fun keyboardHeightInWindow(): Int = composeTestRule.runOnUiThread {
        imeInsetBottom(composeTestRule.activity.window.decorView)
    }

    private fun imeInsetBottom(decorView: android.view.View): Int =
        ViewCompat.getRootWindowInsets(decorView)
            ?.getInsets(WindowInsetsCompat.Type.ime())
            ?.bottom
            ?: 0

    private fun clearPersistedSalt() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        File(targetContext.filesDir, "datastore").deleteRecursively()
    }

    companion object {
        private const val UPPER_FIELD_TAG = "password_field"
        private const val LOWER_FIELD_TAG = "password_confirmation_field"
        private const val FORM_TIMEOUT_MILLIS = 10_000L
        private const val KEYBOARD_TIMEOUT_MILLIS = 10_000L
    }
}
