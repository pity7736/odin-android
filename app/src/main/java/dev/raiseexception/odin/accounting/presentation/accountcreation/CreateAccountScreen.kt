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
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.shared.presentation.AmountField
import dev.raiseexception.odin.shared.presentation.amountInputToRaw
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
    onCreate: (String, String, Currency?, AccountType?, String) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    onCreateSuccess: () -> Unit,
    modifier: Modifier = Modifier,
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
        AmountField(
            value = balance,
            onValueChange = { balance = it },
            label = "Saldo inicial",
            testTag = "balance_field",
            errorMessage = validation?.balanceError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        ChipPicker(
            label = "Moneda",
            errorMessage = validation?.currencyError,
            errorTestTag = "currency_field_error",
        ) {
            for (currency in Currency.entries) {
                FilterChipItem(
                    label = currencyLabel(currency),
                    selected = selectedCurrency == currency,
                    onClick = { selectedCurrency = currency },
                    testTag = "currency_option_${currency.name}",
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ChipPicker(
            label = "Tipo",
            errorMessage = validation?.typeError,
            errorTestTag = "type_field_error",
        ) {
            for (type in AccountType.entries) {
                FilterChipItem(
                    label = typeLabel(type),
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    testTag = "type_option_${type.name}",
                )
            }
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
        CreateAction(uiState) {
            onCreate(name, amountInputToRaw(balance), selectedCurrency, selectedType, description)
        }
        GeneralMessage(uiState)
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
    uiState: CreateAccountUiState,
    onCreate: () -> Unit,
) {
    when (uiState) {
        is CreateAccountUiState.Loading -> Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("loading_indicator"),
            )
        }
        else -> Button(
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

@Composable
private fun GeneralMessage(uiState: CreateAccountUiState) {
    if (uiState is CreateAccountUiState.Error) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.message,
            color = ExpenseRed,
            modifier = Modifier.testTag("error_message"),
        )
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
}
