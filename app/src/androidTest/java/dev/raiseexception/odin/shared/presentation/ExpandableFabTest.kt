package dev.raiseexception.odin.shared.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpandableFabTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_fab_collapsed_when_displayed_then_shows_main_fab_only() {
        composeTestRule.setContent {
            ExpandableFab(
                showTransferOption = true,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("expandable_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_income_fab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("create_expense_fab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("create_transfer_fab").assertDoesNotExist()
    }

    @Test
    fun given_fab_collapsed_when_main_fab_pressed_then_expands_and_shows_all_actions() {
        composeTestRule.setContent {
            ExpandableFab(
                showTransferOption = true,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("expandable_fab").performClick()
        composeTestRule.onNodeWithTag("create_income_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_transfer_fab").assertIsDisplayed()
    }

    @Test
    fun given_fab_expanded_when_main_fab_pressed_then_collapses() {
        composeTestRule.setContent {
            ExpandableFab(
                showTransferOption = true,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("expandable_fab").performClick()
        composeTestRule.onNodeWithTag("create_income_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("expandable_fab").performClick()
        composeTestRule.onNodeWithTag("create_income_fab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("create_expense_fab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("create_transfer_fab").assertDoesNotExist()
    }

    @Test
    fun given_fab_expanded_when_income_selected_then_calls_callback() {
        var incomeCalled = false
        composeTestRule.setContent {
            ExpandableFab(
                showTransferOption = true,
                onIncomeSelected = { incomeCalled = true },
                onExpenseSelected = {},
                onTransferSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("expandable_fab").performClick()
        composeTestRule.onNodeWithTag("create_income_fab").performClick()
        assertTrue(incomeCalled)
    }

    @Test
    fun given_fab_expanded_when_expense_selected_then_calls_callback() {
        var expenseCalled = false
        composeTestRule.setContent {
            ExpandableFab(
                showTransferOption = true,
                onIncomeSelected = {},
                onExpenseSelected = { expenseCalled = true },
                onTransferSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("expandable_fab").performClick()
        composeTestRule.onNodeWithTag("create_expense_fab").performClick()
        assertTrue(expenseCalled)
    }

    @Test
    fun given_fab_expanded_when_transfer_selected_then_calls_callback() {
        var transferCalled = false
        composeTestRule.setContent {
            ExpandableFab(
                showTransferOption = true,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = { transferCalled = true },
            )
        }
        composeTestRule.onNodeWithTag("expandable_fab").performClick()
        composeTestRule.onNodeWithTag("create_transfer_fab").performClick()
        assertTrue(transferCalled)
    }

    @Test
    fun given_show_transfer_false_when_fab_expanded_then_hides_transfer_action() {
        composeTestRule.setContent {
            ExpandableFab(
                showTransferOption = false,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("expandable_fab").performClick()
        composeTestRule.onNodeWithTag("create_income_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_transfer_fab").assertDoesNotExist()
    }
}
