package dev.raiseexception.odin.accounting.presentation.expensecreation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun CreateExpenseScreen(
    uiState: CreateExpenseUiState,
    onSave: (String, String, CategoryInput, String) -> Unit,
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
        is CreateExpenseUiState.Loading -> LoadingContent(modifier)
        is CreateExpenseUiState.Error -> ErrorContent(message = uiState.message, modifier = modifier)
        else -> ExpenseForm(
            categories = when (uiState) {
                is CreateExpenseUiState.Idle -> uiState.categories
                is CreateExpenseUiState.ValidationError -> uiState.categories
                else -> emptyList()
            },
            validation = uiState as? CreateExpenseUiState.ValidationError,
            isSaving = uiState is CreateExpenseUiState.Saving,
            onSave = onSave,
            modifier = modifier
        )
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
private fun ExpenseForm(
    categories: List<Category>,
    validation: CreateExpenseUiState.ValidationError?,
    isSaving: Boolean,
    onSave: (String, String, CategoryInput, String) -> Unit,
    modifier: Modifier = Modifier
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Registrar gasto",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("create_expense_title")
        )
        AmountField(amount, { amount = it }, validation?.amountError)
        DatePickerField(rawDate, { rawDate = it }, validation?.dateError)
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
            onClick = { onSave(amount, rawDate, categoryInput, description) },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_button")
        ) {
            Text("Guardar")
        }
    }
}

@Composable
private fun CategoryAutocomplete(
    categories: List<Category>,
    categoryText: String,
    onCategoryTextChange: (String) -> Unit,
    onSuggestionSelected: (Category) -> Unit,
    errorMessage: String?
) {
    val suggestions = categories.filter { it.name.contains(categoryText.trim(), ignoreCase = true) }
    var isFocused by remember { mutableStateOf(false) }
    var justSelected by remember { mutableStateOf(false) }
    val showMenu = isFocused && !justSelected && suggestions.isNotEmpty()
    LaunchedEffect(errorMessage) { justSelected = false }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = categoryText,
                onValueChange = { text ->
                    onCategoryTextChange(text)
                    justSelected = false
                },
                label = { Text("Categoría") },
                singleLine = true,
                isError = errorMessage != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_field")
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (state.isFocused) justSelected = false
                    }
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { justSelected = true },
                properties = PopupProperties(focusable = false)
            ) {
                suggestions.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onSuggestionSelected(category)
                            justSelected = true
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

private fun resolveCategoryInput(
    categoryText: String,
    selectedCategoryId: String,
    categories: List<Category>
): CategoryInput {
    if (selectedCategoryId.isNotBlank()) {
        val match = categories.firstOrNull { it.id == selectedCategoryId }
        if (match != null) return CategoryInput.Existing(match.id)
    }
    val exactMatch = categories.firstOrNull { it.name.equals(categoryText.trim(), ignoreCase = true) }
    if (exactMatch != null) return CategoryInput.Existing(exactMatch.id)
    return CategoryInput.New(categoryText.trim())
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    errorMessage: String?
) {
    var showPicker by remember { mutableStateOf(false) }
    val todayMillis = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            .toEpochDays().toLong() * MILLIS_PER_DAY
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = todayMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
        }
    )
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is PressInteraction.Release) showPicker = true
        }
    }
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateSelected(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date.toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = selectedDate,
            onValueChange = {},
            label = { Text("Fecha") },
            singleLine = true,
            readOnly = true,
            isError = errorMessage != null,
            interactionSource = interactionSource,
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

private const val MILLIS_PER_DAY = 86_400_000L
