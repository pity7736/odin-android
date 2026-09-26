@file:Suppress("LongMethod", "LongParameterList")

package dev.raiseexception.odin.accounting.presentation.expenseedit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.shared.presentation.AmountField
import dev.raiseexception.odin.shared.presentation.CategoryAutocomplete
import dev.raiseexception.odin.shared.presentation.DatePickerField
import dev.raiseexception.odin.shared.presentation.OdinField
import dev.raiseexception.odin.shared.presentation.amountInputToRaw
import dev.raiseexception.odin.shared.presentation.capitalizeFirst
import dev.raiseexception.odin.shared.presentation.resolveCategoryInput
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate100
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import dev.raiseexception.odin.ui.theme.SoraFamily
import kotlinx.coroutines.flow.Flow

@Composable
fun EditExpenseScreen(
    uiState: EditExpenseUiState,
    onSave: (String, String, CategoryInput, String) -> Unit,
    navigationEvent: Flow<Unit>,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { onSaved() }
    }
    when (uiState) {
        is EditExpenseUiState.Loading -> EditExpenseLoading(modifier)
        is EditExpenseUiState.NotFound -> EditExpenseNotFound(modifier)
        is EditExpenseUiState.Editing -> EditExpenseForm(uiState, onSave, onCancel, modifier)
    }
}

@Composable
private fun EditExpenseForm(
    editing: EditExpenseUiState.Editing,
    onSave: (String, String, CategoryInput, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by rememberSaveable { mutableStateOf(editing.amount.replace('.', ',')) }
    var rawDate by rememberSaveable { mutableStateOf(editing.date) }
    var categoryText by rememberSaveable { mutableStateOf(editing.categoryName) }
    var selectedCategoryId by rememberSaveable { mutableStateOf(editing.categoryId) }
    var description by rememberSaveable { mutableStateOf(editing.description) }
    val categoryInput = resolveCategoryInput(categoryText, selectedCategoryId, editing.categories)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Editar gasto",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            color = Slate900,
            modifier = Modifier.testTag("edit_expense_title"),
        )
        Spacer(modifier = Modifier.height(28.dp))
        ReadOnlyAccount(editing.accountName)
        Spacer(modifier = Modifier.height(16.dp))
        AmountField(
            value = amount,
            onValueChange = { amount = it },
            label = "Monto",
            testTag = "amount_field",
            errorMessage = editing.amountError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        DatePickerField(rawDate, { rawDate = it }, editing.dateError, editing.accountCreatedAt)
        Spacer(modifier = Modifier.height(16.dp))
        CategoryAutocomplete(
            categories = editing.categories,
            categoryText = categoryText,
            onCategoryTextChange = { text ->
                categoryText = text
                selectedCategoryId = ""
            },
            onSuggestionSelected = { category ->
                categoryText = category.name
                selectedCategoryId = category.id
            },
            errorMessage = editing.categoryError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OdinField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción (opcional)",
            testTag = "description_field",
            errorMessage = editing.descriptionError,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SaveAction(isSaving = editing.isSaving) {
            onSave(amountInputToRaw(amount), rawDate, categoryInput, description)
        }
        SaveErrorMessage(editing.saveError)
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cancel_button"),
        ) {
            Text(text = "Cancelar", color = Slate600)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ReadOnlyAccount(accountName: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Cuenta",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Slate100,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = capitalizeFirst(accountName),
                style = MaterialTheme.typography.bodyLarge,
                color = Slate600,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .testTag("account_readonly"),
            )
        }
    }
}

@Composable
private fun SaveAction(isSaving: Boolean, onSave: () -> Unit) {
    Button(
        onClick = onSave,
        enabled = !isSaving,
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
        if (isSaving) {
            CircularProgressIndicator(
                color = Slate50,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(20.dp)
                    .testTag("loading_indicator"),
            )
        } else {
            Text(
                text = "Guardar cambios",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun SaveErrorMessage(saveError: String?) {
    if (saveError != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = saveError,
            color = ExpenseRed,
            modifier = Modifier.testTag("save_error"),
        )
    }
}

@Composable
private fun EditExpenseLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EditExpenseNotFound(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Transacción no encontrada",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("not_found_message"),
        )
    }
}
