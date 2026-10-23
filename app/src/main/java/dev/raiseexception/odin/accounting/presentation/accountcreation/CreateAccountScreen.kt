@file:Suppress("TooManyFunctions", "LongMethod", "LongParameterList")

package dev.raiseexception.odin.accounting.presentation.accountcreation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.shared.presentation.AmountField
import dev.raiseexception.odin.shared.presentation.CategoryAutocomplete
import dev.raiseexception.odin.shared.presentation.TagField
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureContent
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureDialogHost
import dev.raiseexception.odin.shared.presentation.resolveCategoryInput
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import dev.raiseexception.odin.ui.theme.SoraFamily
import kotlinx.coroutines.flow.Flow

@Composable
fun CreateAccountScreen(
    uiState: CreateAccountUiState,
    onCreate: (AccountFormFields) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    unexpectedFailureEvent: Flow<Unit>,
    onCreateSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onTypeSelected: (AccountType) -> Unit = {},
    onCurrencySelected: (Currency) -> Unit = {},
    onGmfExemptChanged: (Boolean) -> Unit = {},
    onTagTextChange: (String) -> Unit = {},
    onAddTag: () -> Unit = {},
    onTagPicked: (Tag) -> Unit = {},
    onTagRemoved: (Int) -> Unit = {},
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { onCreateSuccess() }
    }
    UnexpectedFailureDialogHost(unexpectedFailureEvent)
    when (uiState) {
        is CreateAccountUiState.UnexpectedFailure -> UnexpectedFailureContent(onRetry = onRetry, modifier = modifier)
        is CreateAccountUiState.Loading -> LoadingContent(modifier)
        is CreateAccountUiState.Error -> ErrorContent(uiState.message, modifier)
        is CreateAccountUiState.Idle,
        is CreateAccountUiState.Saving,
        is CreateAccountUiState.ValidationError,
        is CreateAccountUiState.SaveFailed -> CreateAccountForm(
            form = formOf(uiState),
            validation = uiState as? CreateAccountUiState.ValidationError,
            saveError = (uiState as? CreateAccountUiState.SaveFailed)?.message,
            isSaving = uiState is CreateAccountUiState.Saving,
            events = AccountFormEvents(
                onCreate = onCreate,
                onTypeSelected = onTypeSelected,
                onCurrencySelected = onCurrencySelected,
                onGmfExemptChanged = onGmfExemptChanged,
                onTagTextChange = onTagTextChange,
                onAddTag = onAddTag,
                onTagPicked = onTagPicked,
                onTagRemoved = onTagRemoved,
            ),
            modifier = modifier,
        )
    }
}

private fun formOf(uiState: CreateAccountUiState): AccountForm = when (uiState) {
    is CreateAccountUiState.Idle -> uiState.form
    is CreateAccountUiState.Saving -> uiState.form
    is CreateAccountUiState.ValidationError -> uiState.form
    is CreateAccountUiState.SaveFailed -> uiState.form
    else -> AccountForm()
}

private data class AccountFormEvents(
    val onCreate: (AccountFormFields) -> Unit,
    val onTypeSelected: (AccountType) -> Unit = {},
    val onCurrencySelected: (Currency) -> Unit = {},
    val onGmfExemptChanged: (Boolean) -> Unit = {},
    val onTagTextChange: (String) -> Unit = {},
    val onAddTag: () -> Unit = {},
    val onTagPicked: (Tag) -> Unit = {},
    val onTagRemoved: (Int) -> Unit = {},
)

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("form_loading_indicator"),
        )
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = ExpenseRed,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("error_message"),
        )
    }
}

@Composable
private fun SaveErrorMessage(saveError: String?) {
    if (saveError != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = saveError,
            color = ExpenseRed,
            modifier = Modifier.testTag("save_error_message"),
        )
    }
}

@Composable
private fun CreateAccountForm(
    form: AccountForm,
    validation: CreateAccountUiState.ValidationError?,
    saveError: String?,
    isSaving: Boolean,
    events: AccountFormEvents,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var creditLimit by rememberSaveable { mutableStateOf("") }
    var debt by rememberSaveable { mutableStateOf("0") }
    var description by rememberSaveable { mutableStateOf("") }
    var gmfCategoryText by rememberSaveable { mutableStateOf("") }
    var gmfCategoryId by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Nueva cuenta",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            color = Slate900,
            modifier = Modifier.testTag("create_account_title"),
        )
        Spacer(modifier = Modifier.height(28.dp))
        OdinField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre",
            testTag = "name_field",
            errorMessage = validation?.nameError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        ChipPicker(
            label = "Tipo",
            errorMessage = validation?.typeError,
            errorTestTag = "type_field_error",
        ) {
            for (type in AccountType.entries) {
                FilterChipItem(
                    label = typeLabel(type),
                    selected = form.type == type,
                    onClick = { events.onTypeSelected(type) },
                    testTag = "type_option_${type.name}",
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ChipPicker(
            label = "Moneda",
            errorMessage = validation?.currencyError,
            errorTestTag = "currency_field_error",
        ) {
            for (currency in Currency.entries) {
                FilterChipItem(
                    label = currencyLabel(currency),
                    selected = form.currency == currency,
                    onClick = { events.onCurrencySelected(currency) },
                    testTag = "currency_option_${currency.name}",
                )
            }
        }
        if (form.type == AccountType.CREDIT_CARD) {
            Spacer(modifier = Modifier.height(16.dp))
            AmountField(
                value = creditLimit,
                onValueChange = { creditLimit = it },
                label = "Cupo",
                testTag = "credit_limit_field",
                errorMessage = validation?.creditLimitError,
            )
            Spacer(modifier = Modifier.height(16.dp))
            AmountField(
                value = debt,
                onValueChange = { debt = it },
                label = "Deuda actual",
                testTag = "debt_field",
                errorMessage = validation?.debtError,
            )
        } else if (form.type != null) {
            Spacer(modifier = Modifier.height(16.dp))
            AmountField(
                value = balance,
                onValueChange = { balance = it },
                label = "Saldo inicial",
                testTag = "balance_field",
                errorMessage = validation?.balanceError,
            )
        }
        if (form.showsGmfOption) {
            Spacer(modifier = Modifier.height(16.dp))
            GmfSettings(
                gmfExempt = form.gmfExempt,
                onGmfExemptChanged = events.onGmfExemptChanged,
                gmfCategoryText = gmfCategoryText,
                onGmfCategoryTextChange = { text ->
                    gmfCategoryText = text
                    gmfCategoryId = ""
                },
                onGmfCategorySelected = { category ->
                    gmfCategoryText = category.name
                    gmfCategoryId = category.id
                },
                gmfCategoryError = validation?.gmfCategoryError,
                form = form,
                isSaving = isSaving,
                events = events,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        OdinField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción (opcional)",
            testTag = "description_field",
            errorMessage = validation?.descriptionError,
        )
        Spacer(modifier = Modifier.height(24.dp))
        CreateAction(isSaving) {
            events.onCreate(
                AccountFormFields(
                    name = name,
                    balance = balance,
                    creditLimit = creditLimit,
                    debt = debt,
                    description = description,
                    gmfCategoryInput = resolveCategoryInput(gmfCategoryText, gmfCategoryId, form.categories),
                ),
            )
        }
        SaveErrorMessage(saveError)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GmfSettings(
    gmfExempt: Boolean,
    onGmfExemptChanged: (Boolean) -> Unit,
    gmfCategoryText: String,
    onGmfCategoryTextChange: (String) -> Unit,
    onGmfCategorySelected: (Category) -> Unit,
    gmfCategoryError: String?,
    form: AccountForm,
    isSaving: Boolean,
    events: AccountFormEvents,
) {
    GmfExemptSwitch(gmfExempt = gmfExempt, onGmfExemptChanged = onGmfExemptChanged)
    if (!gmfExempt) {
        Spacer(modifier = Modifier.height(16.dp))
        CategoryAutocomplete(
            categories = form.categories,
            categoryText = gmfCategoryText,
            onCategoryTextChange = onGmfCategoryTextChange,
            onSuggestionSelected = onGmfCategorySelected,
            errorMessage = gmfCategoryError,
            label = "Categoría del 4x1000",
        )
        Spacer(modifier = Modifier.height(16.dp))
        TagField(
            selection = form.tagSelection,
            suggestions = form.tagSelection.suggestions(form.tags),
            isEnabled = !isSaving,
            onTextChange = events.onTagTextChange,
            onConfirm = events.onAddTag,
            onSuggestionPicked = events.onTagPicked,
            onRemove = events.onTagRemoved,
            label = "Etiquetas del 4x1000 (opcional)",
        )
    }
}

@Composable
private fun GmfExemptSwitch(gmfExempt: Boolean, onGmfExemptChanged: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Exenta de 4x1000",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = gmfExempt,
            onCheckedChange = onGmfExemptChanged,
            modifier = Modifier.testTag("gmf_exempt_switch"),
        )
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
private fun ChipPicker(
    label: String,
    errorMessage: String?,
    errorTestTag: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
        FieldError(errorMessage, errorTestTag)
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Slate800 else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Slate200),
        modifier = Modifier.testTag(testTag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Slate50 else Slate500,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
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

@Composable
private fun CreateAction(
    isSaving: Boolean,
    onCreate: () -> Unit,
) {
    if (isSaving) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("loading_indicator"),
            )
        }
    } else {
        Button(
            onClick = onCreate,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Slate800,
                contentColor = Slate50,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("create_button"),
        ) {
            Text(
                text = "Crear cuenta",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun currencyLabel(currency: Currency): String = when (currency) {
    Currency.USD -> "Dólar (USD)"
    Currency.EUR -> "Euro (EUR)"
    Currency.COP -> "Peso (COP)"
}

private fun typeLabel(type: AccountType): String = when (type) {
    AccountType.SAVINGS -> "Ahorros"
    AccountType.CASH -> "Efectivo"
    AccountType.CREDIT_CARD -> "Tarjeta de crédito"
}
