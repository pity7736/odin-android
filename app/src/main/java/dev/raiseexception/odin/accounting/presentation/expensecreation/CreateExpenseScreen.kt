@file:Suppress("TooManyFunctions", "LongMethod", "LongParameterList")

package dev.raiseexception.odin.accounting.presentation.expensecreation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.shared.presentation.ThousandSeparatorTransformation
import dev.raiseexception.odin.shared.presentation.capitalizeFirst
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import dev.raiseexception.odin.ui.theme.SoraFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun CreateExpenseScreen(
    uiState: CreateExpenseUiState,
    onSave: (String, String, CategoryInput, String) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    onNavigateBack: (String) -> Unit,
    modifier: Modifier = Modifier,
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
            accountCreatedAt = when (uiState) {
                is CreateExpenseUiState.Idle -> uiState.accountCreatedAt
                is CreateExpenseUiState.ValidationError -> uiState.accountCreatedAt
                else -> null
            },
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
        OdinField(
            value = amount,
            onValueChange = { amount = it },
            label = "Monto",
            testTag = "amount_field",
            errorMessage = validation?.amountError,
            keyboardType = KeyboardType.Decimal,
            visualTransformation = ThousandSeparatorTransformation,
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
                onClick = { onSave(amount, rawDate, categoryInput, description) },
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

@Composable
private fun OdinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    errorMessage: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = errorMessage != null,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                focusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        )
        FieldError(errorMessage, "${testTag}_error")
    }
}

@Composable
private fun CategoryAutocomplete(
    categories: List<Category>,
    categoryText: String,
    onCategoryTextChange: (String) -> Unit,
    onSuggestionSelected: (Category) -> Unit,
    errorMessage: String?,
) {
    val suggestions = categories.filter { it.name.contains(categoryText.trim(), ignoreCase = true) }
    var isFocused by remember { mutableStateOf(false) }
    var justSelected by remember { mutableStateOf(false) }
    val showMenu = isFocused && !justSelected && suggestions.isNotEmpty()
    LaunchedEffect(errorMessage) { justSelected = false }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Categoría",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = categoryText,
                onValueChange = { text ->
                    onCategoryTextChange(text)
                    justSelected = false
                },
                singleLine = true,
                isError = errorMessage != null,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                    focusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_field")
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (state.isFocused) justSelected = false
                    },
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { justSelected = true },
                properties = PopupProperties(focusable = false),
                modifier = Modifier.background(Color.White),
            ) {
                suggestions.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = capitalizeFirst(category.name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate800,
                            )
                        },
                        onClick = {
                            onSuggestionSelected(category)
                            justSelected = true
                        },
                        modifier = Modifier.testTag("category_option_${category.id}"),
                    )
                }
            }
        }
        FieldError(errorMessage, "category_field_error")
    }
}

private fun resolveCategoryInput(
    categoryText: String,
    selectedCategoryId: String,
    categories: List<Category>,
): CategoryInput {
    if (selectedCategoryId.isNotBlank()) {
        val match = categories.firstOrNull { it.id == selectedCategoryId }
        if (match != null) return CategoryInput.Existing(match.id)
    }
    val exactMatch = categories.firstOrNull { it.name.equals(categoryText.trim(), ignoreCase = true) }
    if (exactMatch != null) return CategoryInput.Existing(exactMatch.id)
    return CategoryInput.New(categoryText.trim())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    errorMessage: String?,
    minDate: LocalDate?,
) {
    var showPicker by remember { mutableStateOf(false) }
    val todayMillis = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            .toEpochDays().toLong() * MILLIS_PER_DAY
    }
    val minDateMillis = remember(minDate) {
        minDate?.toEpochDays()?.toLong()?.times(MILLIS_PER_DAY)
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = todayMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val aboveMin = minDateMillis == null || utcTimeMillis >= minDateMillis
                return utcTimeMillis <= todayMillis && aboveMin
            }
        },
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
                }) { Text("OK", color = Slate800) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar", color = Slate500) }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color.White,
            ),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    titleContentColor = Slate900,
                    headlineContentColor = Slate900,
                    weekdayContentColor = Slate500,
                    navigationContentColor = Slate800,
                    yearContentColor = Slate800,
                    selectedDayContainerColor = Slate800,
                    selectedDayContentColor = Slate50,
                    selectedYearContainerColor = Slate800,
                    selectedYearContentColor = Slate50,
                    todayContentColor = Slate800,
                    todayDateBorderColor = Slate800,
                    dayContentColor = Slate900,
                ),
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Fecha",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = selectedDate,
            onValueChange = {},
            singleLine = true,
            readOnly = true,
            isError = errorMessage != null,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                focusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("date_field"),
        )
        FieldError(errorMessage, "date_field_error")
    }
}

@Composable
private fun FieldError(errorMessage: String?, testTag: String) {
    if (errorMessage != null) {
        Text(
            text = errorMessage,
            color = ExpenseRed,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag(testTag),
        )
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
