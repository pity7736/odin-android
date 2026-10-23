package dev.raiseexception.odin.accounting.presentation.transactiondetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.IncomeGreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun given_content_state_with_income_when_displayed_then_shows_income_details() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = TransactionDetailUiState.Content(
                    formattedAmount = "+$1.000,00",
                    amountColor = IncomeGreen,
                    formattedDate = "14 de septiembre de 2026",
                    categoryName = "Salario",
                    accountName = "Ahorros",
                    description = "Pago mensual",
                    isIncome = true,
                    typeLabel = "Ingreso",
                    isEditable = false,
                ),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithText("+$1.000,00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ingreso").assertIsDisplayed()
        composeTestRule.onNodeWithText("14 de septiembre de 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Salario").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ahorros").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pago mensual").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_icon").assertIsDisplayed()
    }

    @Test
    fun given_content_state_with_expense_when_displayed_then_shows_expense_details() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = TransactionDetailUiState.Content(
                    formattedAmount = "-$500,00",
                    amountColor = ExpenseRed,
                    formattedDate = "15 de septiembre de 2026",
                    categoryName = "Alimentación",
                    accountName = "Efectivo",
                    description = "Mercado semanal",
                    isIncome = false,
                    typeLabel = "Gasto",
                    isEditable = true,
                ),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithText("-$500,00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gasto").assertIsDisplayed()
        composeTestRule.onNodeWithText("15 de septiembre de 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alimentación").assertIsDisplayed()
        composeTestRule.onNodeWithText("Efectivo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mercado semanal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_icon").assertIsDisplayed()
    }

    @Test
    fun given_content_state_with_empty_description_when_displayed_then_hides_description() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = TransactionDetailUiState.Content(
                    formattedAmount = "+$1.000,00",
                    amountColor = IncomeGreen,
                    formattedDate = "14 de septiembre de 2026",
                    categoryName = "Salario",
                    accountName = "Ahorros",
                    description = "",
                    isIncome = true,
                    typeLabel = "Ingreso",
                    isEditable = false,
                ),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithText("+$1.000,00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("description_text").assertDoesNotExist()
    }

    @Test
    fun given_loading_state_when_displayed_then_shows_loading_indicator() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = TransactionDetailUiState.Loading,
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun given_not_found_state_when_displayed_then_shows_not_found_message() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = TransactionDetailUiState.NotFound,
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithText("Transacción no encontrada").assertIsDisplayed()
    }

    @Test
    fun given_error_state_when_displayed_then_shows_error_message() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = TransactionDetailUiState.Error(message = "Error al acceder a los datos"),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithText("Error al acceder a los datos").assertIsDisplayed()
    }

    @Test
    fun given_editable_content_when_shown_then_the_editar_action_is_displayed_and_clicking_it_calls_on_edit_expense() {
        var editClicks = 0
        var incomeEditClicks = 0
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = expenseContent(isEditable = true),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = { editClicks++ },
                onEditIncome = { incomeEditClicks++ },
            )
        }
        composeTestRule.onNodeWithTag("edit_transaction_button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Editar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_transaction_button").performClick()
        assertEquals(1, editClicks)
        assertEquals(0, incomeEditClicks)
    }

    @Test
    fun given_editable_income_content_when_clicking_editar_then_on_edit_income_is_called() {
        var expenseEditClicks = 0
        var incomeEditClicks = 0
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = incomeContent(isEditable = true),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = { expenseEditClicks++ },
                onEditIncome = { incomeEditClicks++ },
            )
        }
        composeTestRule.onNodeWithContentDescription("Editar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_transaction_button").performClick()
        assertEquals(1, incomeEditClicks)
        assertEquals(0, expenseEditClicks)
    }

    @Test
    fun given_non_editable_income_content_when_shown_then_no_editar_action_is_displayed() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = incomeContent(isEditable = false),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithTag("edit_transaction_button").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Editar").assertDoesNotExist()
    }

    @Test
    fun given_non_editable_content_when_shown_then_no_editar_action_is_displayed() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = expenseContent(isEditable = false),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithTag("edit_transaction_button").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Editar").assertDoesNotExist()
    }

    @Test
    fun given_content_state_of_a_card_payment_when_displayed_then_names_it_pago() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = TransactionDetailUiState.Content(
                    formattedAmount = "+$300.000,00",
                    amountColor = IncomeGreen,
                    formattedDate = "10 de septiembre de 2026",
                    categoryName = "Transferencia",
                    accountName = "Visa",
                    description = "Pago desde Ahorros",
                    isIncome = true,
                    typeLabel = "Pago",
                    isEditable = false,
                ),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithText("Pago").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ingreso").assertDoesNotExist()
    }

    @Test
    fun given_content_with_tags_when_displayed_then_shows_etiquetas_with_a_chip_per_tag_in_order() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = expenseContent(isEditable = true).copy(tagNames = listOf("Comida", "Nala")),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithText("ETIQUETAS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tag_detail_chip_0").assertTextEquals("Comida")
        composeTestRule.onNodeWithTag("tag_detail_chip_1").assertTextEquals("Nala")
    }

    @Test
    fun given_content_without_tags_when_displayed_then_nothing_about_tags_is_shown() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = expenseContent(isEditable = true),
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithText("ETIQUETAS").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tags_chips").assertDoesNotExist()
    }

    @Test
    fun given_editable_content_when_shown_then_the_edit_and_delete_icons_are_displayed() {
        setDetailContent(expenseContent(isEditable = true))
        composeTestRule.onNodeWithTag("edit_transaction_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_transaction_button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Eliminar").assertIsDisplayed()
    }

    @Test
    fun given_non_editable_content_when_shown_then_neither_the_edit_nor_the_delete_icon_is_displayed() {
        setDetailContent(expenseContent(isEditable = false))
        composeTestRule.onNodeWithTag("edit_transaction_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("delete_transaction_button").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Eliminar").assertDoesNotExist()
    }

    @Test
    fun given_an_expense_when_tapping_delete_then_asks_to_confirm_deleting_the_expense() {
        setDetailContent(expenseContent(isEditable = true))
        composeTestRule.onNodeWithTag("delete_transaction_button").performClick()
        composeTestRule.onNodeWithTag("delete_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("¿Eliminar este gasto? Esta acción no se puede deshacer.").assertIsDisplayed()
    }

    @Test
    fun given_an_income_when_tapping_delete_then_asks_to_confirm_deleting_the_income() {
        setDetailContent(incomeContent(isEditable = true))
        composeTestRule.onNodeWithTag("delete_transaction_button").performClick()
        composeTestRule.onNodeWithText("¿Eliminar este ingreso? Esta acción no se puede deshacer.").assertIsDisplayed()
    }

    @Test
    fun given_the_confirmation_when_tapping_cancelar_then_it_closes_without_deleting() {
        var deleteRequests = 0
        setDetailContent(expenseContent(isEditable = true), onDelete = { deleteRequests++ })
        composeTestRule.onNodeWithTag("delete_transaction_button").performClick()
        composeTestRule.onNodeWithTag("delete_confirmation_cancel").performClick()
        composeTestRule.onNodeWithTag("delete_confirmation_dialog").assertDoesNotExist()
        assertEquals(0, deleteRequests)
    }

    @Test
    fun given_the_confirmation_when_tapping_eliminar_then_on_delete_is_called() {
        var deleteRequests = 0
        setDetailContent(expenseContent(isEditable = true), onDelete = { deleteRequests++ })
        composeTestRule.onNodeWithTag("delete_transaction_button").performClick()
        composeTestRule.onNodeWithTag("delete_confirmation_confirm").performClick()
        composeTestRule.onNodeWithTag("delete_confirmation_dialog").assertDoesNotExist()
        assertEquals(1, deleteRequests)
    }

    @Test
    fun given_a_deletion_in_progress_when_shown_then_the_edit_and_delete_icons_are_disabled() {
        setDetailContent(expenseContent(isEditable = true).copy(isDeleting = true))
        composeTestRule.onNodeWithTag("edit_transaction_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("delete_transaction_button").assertIsNotEnabled()
    }

    @Test
    fun given_a_deletion_message_when_shown_then_entendido_closes_it() {
        val message = "El saldo de la cuenta quedaría negativo el 8 de marzo."
        setDetailContent(expenseContent(isEditable = true), deletionMessageEvent = flowOf(message))
        composeTestRule.onNodeWithTag("deletion_message_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithText("Entendido").performClick()
        composeTestRule.onNodeWithTag("deletion_message_dialog").assertDoesNotExist()
    }

    @Test
    fun given_a_deletion_in_progress_when_tapping_the_bottom_bar_then_does_not_leave() {
        var homeNavigations = 0
        setDetailContent(expenseContent(isEditable = true).copy(isDeleting = true), onNavigateToHome = { homeNavigations++ })
        composeTestRule.onNodeWithTag("nav_home").performClick()
        assertEquals(0, homeNavigations)
    }

    @Test
    fun given_no_deletion_in_progress_when_tapping_the_bottom_bar_then_leaves() {
        var homeNavigations = 0
        setDetailContent(expenseContent(isEditable = true), onNavigateToHome = { homeNavigations++ })
        composeTestRule.onNodeWithTag("nav_home").performClick()
        assertEquals(1, homeNavigations)
    }

    private fun setDetailContent(
        uiState: TransactionDetailUiState,
        onDelete: () -> Unit = {},
        deletionMessageEvent: Flow<String> = emptyFlow(),
        onNavigateToHome: () -> Unit = {},
        onOpenTransaction: (String) -> Unit = {}
    ) {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = onDelete,
                deletionMessageEvent = deletionMessageEvent,
                unexpectedFailureEvent = emptyFlow(),
                uiState = uiState,
                onNavigateToHome = onNavigateToHome,
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
                onOpenTransaction = onOpenTransaction,
            )
        }
    }

    @Test
    fun given_Mercado_with_its_tax_when_displayed_then_shows_the_tax_field_last() {
        setDetailContent(mercadoWithTax().copy(description = "Mercado", tagNames = listOf("Hogar")))
        composeTestRule.onNodeWithTag("gmf_tax_field").assertIsDisplayed()
        composeTestRule.onNodeWithText("4X1000").assertIsDisplayed()
        composeTestRule.onNodeWithText("$400,00").assertIsDisplayed()
        val tagsBottom = composeTestRule.onNodeWithTag("tags_chips").getUnclippedBoundsInRoot().bottom
        val taxTop = composeTestRule.onNodeWithTag("gmf_tax_field").getUnclippedBoundsInRoot().top
        assertTrue(taxTop > tagsBottom)
    }

    @Test
    fun given_Mercado_with_its_tax_when_opening_the_tax_field_then_opens_the_details_of_its_tax() {
        var openedTransactionId: String? = null
        setDetailContent(mercadoWithTax(), onOpenTransaction = { openedTransactionId = it })
        composeTestRule.onNodeWithTag("gmf_tax_field").performClick()
        assertEquals("tax-1", openedTransactionId)
    }

    @Test
    fun given_an_expense_without_a_tax_when_displayed_then_no_tax_field_is_shown() {
        setDetailContent(expenseContent(isEditable = true))
        composeTestRule.onNodeWithTag("gmf_tax_field").assertDoesNotExist()
    }

    @Test
    fun given_Mercado_with_its_tax_when_tapping_delete_then_the_confirmation_mentions_its_tax() {
        setDetailContent(mercadoWithTax())
        composeTestRule.onNodeWithTag("delete_transaction_button").performClick()
        composeTestRule.onNodeWithText(
            "¿Eliminar este gasto? También se eliminará su 4x1000. Esta acción no se puede deshacer."
        ).assertIsDisplayed()
    }

    @Test
    fun given_the_details_of_a_tax_when_displayed_then_offers_no_way_to_edit_or_delete_it() {
        setDetailContent(expenseContent(isEditable = false).copy(description = "4x1000 – Mercado"))
        composeTestRule.onNodeWithTag("edit_transaction_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("delete_transaction_button").assertDoesNotExist()
    }

    private fun mercadoWithTax() =
        expenseContent(isEditable = true).copy(gmfTaxAmount = "$400,00", gmfTaxId = "tax-1")

    private fun incomeContent(isEditable: Boolean) = TransactionDetailUiState.Content(
        formattedAmount = "+$1.000,00",
        amountColor = IncomeGreen,
        formattedDate = "14 de septiembre de 2026",
        categoryName = "Salario",
        accountName = "Ahorros",
        description = "Pago mensual",
        isIncome = true,
        typeLabel = "Ingreso",
        isEditable = isEditable,
    )

    private fun expenseContent(isEditable: Boolean) = TransactionDetailUiState.Content(
        formattedAmount = "-$500,00",
        amountColor = ExpenseRed,
        formattedDate = "15 de septiembre de 2026",
        categoryName = "Alimentación",
        accountName = "Efectivo",
        description = "Mercado semanal",
        isIncome = false,
        typeLabel = "Gasto",
        isEditable = isEditable,
    )

    @Test
    fun given_an_unexpected_failure_when_displayed_then_shows_the_retry_content_and_no_information() {
        composeTestRule.setContent {
            TransactionDetailScreen(
                onRetry = {},
                onDelete = {},
                deletionMessageEvent = emptyFlow(),
                unexpectedFailureEvent = emptyFlow(),
                uiState = TransactionDetailUiState.UnexpectedFailure,
                onNavigateToHome = {},
                onNavigateToAccounts = {},
                onNavigateToCategories = {},
                onNavigateToReports = {},
                onEditExpense = {},
                onEditIncome = {},
            )
        }
        composeTestRule.onNodeWithTag("unexpected_failure_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unexpected_failure_retry").assertIsDisplayed()
        composeTestRule.onNodeWithTag("amount_text").assertDoesNotExist()
        composeTestRule.onNodeWithTag("loading_indicator").assertDoesNotExist()
    }
}
