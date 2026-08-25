package dev.raiseexception.odin.accounting.presentation.accountcreation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import kotlinx.coroutines.flow.Flow

@Composable
fun CreateAccountScreen(
    uiState: CreateAccountUiState,
    onCreate: (String, String, Currency?, AccountType?, String) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    onCreateSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { onCreateSuccess() }
    }
    var name by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var selectedCurrency by rememberSaveable { mutableStateOf<Currency?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<AccountType?>(null) }
    val validation = uiState as? CreateAccountUiState.ValidationError
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Nueva cuenta",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("create_account_title")
        )
        LabeledField(name, { name = it }, "Nombre", "name_field", validation?.nameError)
        BalanceField(balance, { balance = it }, validation?.balanceError)
        CurrencyPicker(selectedCurrency, { selectedCurrency = it }, validation?.currencyError)
        TypePicker(selectedType, { selectedType = it }, validation?.typeError)
        LabeledField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción (opcional)",
            testTag = "description_field",
            errorMessage = validation?.descriptionError
        )
        CreateAction(uiState) { onCreate(name, balance, selectedCurrency, selectedType, description) }
        GeneralMessage(uiState)
    }
}

@Composable
private fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = errorMessage != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
        FieldError(errorMessage, "${testTag}_error")
    }
}

@Composable
private fun BalanceField(
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Saldo inicial") },
            singleLine = true,
            isError = errorMessage != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("balance_field")
        )
        FieldError(errorMessage, "balance_field_error")
    }
}

@Composable
private fun CurrencyPicker(
    selectedCurrency: Currency?,
    onSelect: (Currency) -> Unit,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Moneda", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (currency in Currency.entries) {
                FilterChip(
                    selected = selectedCurrency == currency,
                    onClick = { onSelect(currency) },
                    label = { Text(currencyLabel(currency)) },
                    modifier = Modifier.testTag("currency_option_${currency.name}")
                )
            }
        }
        FieldError(errorMessage, "currency_field_error")
    }
}

@Composable
private fun TypePicker(
    selectedType: AccountType?,
    onSelect: (AccountType) -> Unit,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Tipo", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in AccountType.entries) {
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onSelect(type) },
                    label = { Text(typeLabel(type)) },
                    modifier = Modifier.testTag("type_option_${type.name}")
                )
            }
        }
        FieldError(errorMessage, "type_field_error")
    }
}

@Composable
private fun FieldError(errorMessage: String?, testTag: String) {
    if (errorMessage != null) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun CreateAction(
    uiState: CreateAccountUiState,
    onCreate: () -> Unit
) {
    when (uiState) {
        is CreateAccountUiState.Loading -> CircularProgressIndicator(
            modifier = Modifier.testTag("loading_indicator")
        )
        else -> Button(
            onClick = onCreate,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_button")
        ) {
            Text("Crear cuenta")
        }
    }
}

@Composable
private fun GeneralMessage(uiState: CreateAccountUiState) {
    if (uiState is CreateAccountUiState.Error) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("error_message")
        )
    }
}

private fun currencyLabel(currency: Currency): String = when (currency) {
    Currency.USD -> "Dólar (USD)"
    Currency.EUR -> "Euro (EUR)"
    Currency.COP -> "Peso colombiano (COP)"
}

private fun typeLabel(type: AccountType): String = when (type) {
    AccountType.SAVINGS -> "Ahorros"
    AccountType.CASH -> "Efectivo"
}
