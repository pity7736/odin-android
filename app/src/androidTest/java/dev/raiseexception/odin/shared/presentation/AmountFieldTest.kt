package dev.raiseexception.odin.shared.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class AmountFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setAmountField() {
        composeTestRule.setContent {
            var value by remember { mutableStateOf("") }
            AmountField(
                value = value,
                onValueChange = { value = it },
                label = "Monto",
                testTag = "amount_field",
                errorMessage = null,
            )
        }
    }

    @Test
    fun given_amount_field_when_user_types_a_whole_number_then_it_is_grouped_with_dots() {
        setAmountField()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("1500000")
        composeTestRule.onNodeWithText("1.500.000").assertIsDisplayed()
    }

    @Test
    fun given_amount_field_when_user_types_a_comma_decimal_then_the_whole_part_is_grouped_and_the_comma_is_kept() {
        setAmountField()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("111176,46")
        composeTestRule.onNodeWithText("111.176,46").assertIsDisplayed()
    }

    @Test
    fun given_amount_field_when_user_types_a_dot_then_the_dot_is_ignored() {
        setAmountField()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("12.34")
        composeTestRule.onNodeWithText("1.234").assertIsDisplayed()
    }

    @Test
    fun given_amount_field_when_user_types_a_second_comma_then_it_is_ignored() {
        setAmountField()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("12,3,4")
        composeTestRule.onNodeWithText("12,34").assertIsDisplayed()
    }

    @Test
    fun given_amount_field_when_user_types_a_third_decimal_digit_then_it_is_ignored() {
        setAmountField()
        composeTestRule.onNodeWithTag("amount_field").performTextInput("12,345")
        composeTestRule.onNodeWithText("12,34").assertIsDisplayed()
    }
}
