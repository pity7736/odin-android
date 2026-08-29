package dev.raiseexception.odin.accounting.presentation.incomecreation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Composable
fun CreateIncomeScreen(
    uiState: CreateIncomeUiState,
    onSave: (String, LocalDate?, String, String) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    onNavigateBack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { target ->
            when (target) {
                is NavigationTarget.AccountDetail -> onNavigateBack(target.accountId)
            }
        }
    }
    when (uiState) {
        is CreateIncomeUiState.Loading -> LoadingContent(modifier)
        is CreateIncomeUiState.Saving -> LoadingContent(modifier)
        is CreateIncomeUiState.Idle -> IncomeForm(
            categories = uiState.categories,
            validation = null,
            onSave = onSave,
            modifier = modifier
        )
        is CreateIncomeUiState.ValidationError -> IncomeForm(
            categories = uiState.categories,
            validation = uiState,
            onSave = onSave,
            modifier = modifier
        )
        is CreateIncomeUiState.Error -> ErrorContent(message = uiState.message, modifier = modifier)
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator"))
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("error_message")
        )
    }
}

@Composable
private fun IncomeForm(
    categories: List<Category>,
    validation: CreateIncomeUiState.ValidationError?,
    onSave: (String, LocalDate?, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var rawDate by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val parsedDate = parseDate(rawDate)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Registrar ingreso",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("create_income_title")
        )
        AmountField(amount, { amount = it }, validation?.amountError)
        DateField(rawDate, { rawDate = it }, validation?.dateError)
        CategoryDropdown(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onSelect = { selectedCategoryId = it },
            errorMessage = validation?.categoryError
        )
        TextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción (opcional)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("description_field")
        )
        Button(
            onClick = { onSave(amount, parsedDate, selectedCategoryId, description) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_button")
        ) {
            Text("Guardar")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selectedCategoryId: String,
    onSelect: (String) -> Unit,
    errorMessage: String?
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }
    Column(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = selectedCategory?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoría") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = errorMessage != null,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .testTag("category_field")
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onSelect(category.id)
                            expanded = false
                        },
                        modifier = Modifier.testTag("category_option_${category.id}")
                    )
                }
            }
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("category_field_error")
            )
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Monto") },
            singleLine = true,
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("amount_field")
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("amount_field_error")
            )
        }
    }
}

@Composable
private fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Fecha (AAAA-MM-DD)") },
            singleLine = true,
            isError = errorMessage != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("date_field")
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("date_field_error")
            )
        }
    }
}

private fun parseDate(rawDate: String): LocalDate? = try {
    LocalDate.parse(rawDate.trim())
} catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
    null
}
