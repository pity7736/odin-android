package dev.raiseexception.odin.accounting.presentation.categorieslist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test

class CategoriesListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_an_unexpected_failure_when_displayed_then_shows_the_retry_content_and_no_information() {
        composeTestRule.setContent {
            CategoriesListScreen(
                onRetry = {},
                uiState = CategoriesListUiState.UnexpectedFailure,
                navigationEvent = emptyFlow(),
                onCreateCategory = {},
                onFilterChanged = {},
                onSearchQueryChanged = {},
                onCategorySelected = {},
                onNavigateToCategoryDetail = {},
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
            )
        }
        composeTestRule.onNodeWithTag("unexpected_failure_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unexpected_failure_retry").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_categories_message").assertDoesNotExist()
        composeTestRule.onNodeWithTag("search_field").assertDoesNotExist()
    }
}
