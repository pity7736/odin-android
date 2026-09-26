@file:Suppress("TooManyFunctions", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")

package dev.raiseexception.odin.accounting.presentation.expensecreation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.shared.presentation.AccountAutocomplete
import dev.raiseexception.odin.shared.presentation.AmountField
import dev.raiseexception.odin.shared.presentation.CategoryAutocomplete
import dev.raiseexception.odin.shared.presentation.DatePickerField
import dev.raiseexception.odin.shared.presentation.OdinField
import dev.raiseexception.odin.shared.presentation.amountInputToRaw
import dev.raiseexception.odin.shared.presentation.resolveCategoryInput
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import dev.raiseexception.odin.ui.theme.SoraFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun CreateExpenseScreen(
    uiState: CreateExpenseUiState,
    onSave: (String, String, CategoryInput, String) -> Unit,
    onAccountSelected: (String) -> Unit = {},
    navigationEvent: Flow<NavigationTarget>,
    onNavigateBack: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { target ->
            when (target) {
                is NavigationTarget.AccountDetail -> onNavigateBack(target.accountId)
                is NavigationTarget.Back -> onNavigateBack("")
            }
        }
    }
    when (uiState) {
        is CreateExpenseUiState.Loading -> LoadingContent(modifier)
        is CreateExpenseUiState.Error -> ErrorContent(message = uiState.message, modifier = modifier)
        else -> ExpenseForm(
            categories = when (uiState) {
                is CreateExpenseUiState.Idle -> uiState.categories
                is CreateExpenseUiState.ValidationError -> uiState.categories
                else -> emptyList()
            },
            accountCreatedAt = when (uiState) {
                is CreateExpenseUiState.Idle -> uiState.accountCreatedAt
                is CreateExpenseUiState.ValidationError -> uiState.accountCreatedAt
                else -> null
            },
            accounts = when (uiState) {
                is CreateExpenseUiState.Idle -> uiState.accounts
                is CreateExpenseUiState.ValidationError -> uiState.accounts
                else -> emptyList()
            },
            selectedAccountId = when (uiState) {
                is CreateExpenseUiState.Idle -> uiState.selectedAccountId
                is CreateExpenseUiState.ValidationError -> uiState.selectedAccountId
                else -> null
            },
            accountError = (uiState as? CreateExpenseUiState.ValidationError)?.accountError,
            onAccountSelected = onAccountSelected,
            validation = uiState as? CreateExpenseUiState.ValidationError,
            isSaving = uiState is CreateExpenseUiState.Saving,
            onSave = onSave,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("loading_indicator"),
        )
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = ExpenseRed,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("error_message"),
        )
    }
}

@Composable
private fun ExpenseForm(
    categories: List<Category>,
    accountCreatedAt: LocalDate?,
    accounts: List<dev.raiseexception.odin.accounting.domain.model.Account>,
    selectedAccountId: String?,
    accountError: String?,
    onAccountSelected: (String) -> Unit,
    validation: CreateExpenseUiState.ValidationError?,
    isSaving: Boolean,
    onSave: (String, String, CategoryInput, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var rawDate by rememberSaveable {
        mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString())
    }
    var categoryText by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val categoryInput = resolveCategoryInput(categoryText, selectedCategoryId, categories)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Registrar gasto",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            color = Slate900,
            modifier = Modifier.testTag("create_expense_title"),
        )
        Spacer(modifier = Modifier.height(28.dp))
        if (accounts.isNotEmpty()) {
            AccountAutocomplete(
                accounts = accounts,
                selectedAccountId = selectedAccountId,
                onAccountSelected = onAccountSelected,
                isError = accountError != null,
                errorMessage = accountError,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        AmountField(
            value = amount,
            onValueChange = { amount = it },
            label = "Monto",
            testTag = "amount_field",
            errorMessage = validation?.amountError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        DatePickerField(rawDate, { rawDate = it }, validation?.dateError, accountCreatedAt)
        Spacer(modifier = Modifier.height(16.dp))
        CategoryAutocomplete(
            categories = categories,
            categoryText = categoryText,
            onCategoryTextChange = { text ->
                categoryText = text
                selectedCategoryId = ""
            },
            onSuggestionSelected = { category ->
                categoryText = category.name
                selectedCategoryId = category.id
            },
            errorMessage = validation?.categoryError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OdinField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción (opcional)",
            testTag = "description_field",
            errorMessage = null,
        )
        Spacer(modifier = Modifier.height(24.dp))
        when {
            isSaving -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> Button(
                onClick = { onSave(amountInputToRaw(amount), rawDate, categoryInput, description) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Slate800,
                    contentColor = Slate50,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_button"),
            ) {
                Text(
                    text = "Guardar",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
