package dev.raiseexception.odin.shared.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
                expanded = false,
                onToggle = {},
                showIncomeOption = true,
                showTransferOption = true,
                showPaymentOption = false,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = {},
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
            var expanded by remember { mutableStateOf(false) }
            ExpandableFab(
                expanded = expanded,
                onToggle = { expanded = !expanded },
                showIncomeOption = true,
                showTransferOption = true,
                showPaymentOption = false,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = {},
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
            var expanded by remember { mutableStateOf(false) }
            ExpandableFab(
                expanded = expanded,
                onToggle = { expanded = !expanded },
                showIncomeOption = true,
                showTransferOption = true,
                showPaymentOption = false,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = {},
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
                expanded = true,
                onToggle = {},
                showIncomeOption = true,
                showTransferOption = true,
                showPaymentOption = false,
                onIncomeSelected = { incomeCalled = true },
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("create_income_fab").performClick()
        assertTrue(incomeCalled)
    }

    @Test
    fun given_fab_expanded_when_expense_selected_then_calls_callback() {
        var expenseCalled = false
        composeTestRule.setContent {
            ExpandableFab(
                expanded = true,
                onToggle = {},
                showIncomeOption = true,
                showTransferOption = true,
                showPaymentOption = false,
                onIncomeSelected = {},
                onExpenseSelected = { expenseCalled = true },
                onTransferSelected = {},
                onPaymentSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("create_expense_fab").performClick()
        assertTrue(expenseCalled)
    }

    @Test
    fun given_fab_expanded_when_transfer_selected_then_calls_callback() {
        var transferCalled = false
        composeTestRule.setContent {
            ExpandableFab(
                expanded = true,
                onToggle = {},
                showIncomeOption = true,
                showTransferOption = true,
                showPaymentOption = false,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = { transferCalled = true },
                onPaymentSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("create_transfer_fab").performClick()
        assertTrue(transferCalled)
    }

    @Test
    fun given_show_transfer_false_when_fab_expanded_then_hides_transfer_action() {
        composeTestRule.setContent {
            ExpandableFab(
                expanded = true,
                onToggle = {},
                showIncomeOption = true,
                showTransferOption = false,
                showPaymentOption = false,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("create_income_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_transfer_fab").assertDoesNotExist()
    }

    @Test
    fun given_show_income_and_transfer_false_when_fab_expanded_then_shows_only_expense_action() {
        composeTestRule.setContent {
            ExpandableFab(
                expanded = true,
                onToggle = {},
                showIncomeOption = false,
                showTransferOption = false,
                showPaymentOption = false,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("create_expense_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_income_fab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("create_transfer_fab").assertDoesNotExist()
    }

    @Test
    fun given_show_income_true_when_fab_expanded_then_shows_income_action() {
        composeTestRule.setContent {
            ExpandableFab(
                expanded = true,
                onToggle = {},
                showIncomeOption = true,
                showTransferOption = false,
                showPaymentOption = false,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("create_income_fab").assertIsDisplayed()
    }

    @Test
    fun given_show_payment_true_when_fab_expanded_then_shows_the_payment_action_labeled_pago() {
        composeTestRule.setContent {
            ExpandableFab(
                expanded = true,
                onToggle = {},
                showIncomeOption = false,
                showTransferOption = false,
                showPaymentOption = true,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("create_payment_fab").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pago").assertIsDisplayed()
    }

    @Test
    fun given_show_payment_false_when_fab_expanded_then_hides_the_payment_action() {
        composeTestRule.setContent {
            ExpandableFab(
                expanded = true,
                onToggle = {},
                showIncomeOption = true,
                showTransferOption = true,
                showPaymentOption = false,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = {},
            )
        }
        composeTestRule.onNodeWithTag("create_payment_fab").assertDoesNotExist()
    }

    @Test
    fun given_fab_expanded_when_payment_selected_then_calls_callback() {
        var paymentCalled = false
        composeTestRule.setContent {
            ExpandableFab(
                expanded = true,
                onToggle = {},
                showIncomeOption = false,
                showTransferOption = false,
                showPaymentOption = true,
                onIncomeSelected = {},
                onExpenseSelected = {},
                onTransferSelected = {},
                onPaymentSelected = { paymentCalled = true },
            )
        }
        composeTestRule.onNodeWithTag("create_payment_fab").performClick()
        assertTrue(paymentCalled)
    }
}
