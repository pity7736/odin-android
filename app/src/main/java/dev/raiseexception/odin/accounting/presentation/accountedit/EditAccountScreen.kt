@file:Suppress("TooManyFunctions", "LongMethod", "LongParameterList")

package dev.raiseexception.odin.accounting.presentation.accountedit

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
import androidx.compose.material3.TextButton
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
import dev.raiseexception.odin.ui.theme.Slate100
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import dev.raiseexception.odin.ui.theme.SoraFamily
import kotlinx.coroutines.flow.Flow

private const val LOCK_MESSAGE =
    "No puedes cambiar la moneda ni el saldo inicial porque la cuenta ya tiene movimientos."

@Composable
fun EditAccountScreen(
    uiState: EditAccountUiState,
    onSave: (String, String, Currency?, AccountType?, String) -> Unit,
    navigationEvent: Flow<Unit>,
    onSaved: () -> Unit,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { onSaved() }
    }
    when (uiState) {
        is EditAccountUiState.Loading -> EditAccountLoading(modifier)
        is EditAccountUiState.NotFound -> EditAccountMessage(modifier)
        is EditAccountUiState.Editing -> EditAccountForm(uiState, onSave, onCancel, modifier)
    }
}

@Composable
private fun EditAccountForm(
    editing: EditAccountUiState.Editing,
    onSave: (String, String, Currency?, AccountType?, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(editing.name) }
    var balance by rememberSaveable { mutableStateOf(editing.initialBalance.replace('.', ',')) }
    var description by rememberSaveable { mutableStateOf(editing.description) }
    var selectedCurrency by rememberSaveable { mutableStateOf(editing.currency) }
    var selectedType by rememberSaveable { mutableStateOf(editing.type) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Editar cuenta",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            color = Slate900,
            modifier = Modifier.testTag("edit_account_title"),
        )
        Spacer(modifier = Modifier.height(28.dp))
        OdinField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre",
            testTag = "name_field",
            errorMessage = editing.nameError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (editing.locked) {
            LockedBalance(editing.lockedBalanceDisplay ?: "")
            Spacer(modifier = Modifier.height(16.dp))
            LockedCurrency(editing.currency)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = LOCK_MESSAGE,
                style = MaterialTheme.typography.bodySmall,
                color = Slate600,
                modifier = Modifier.testTag("lock_message"),
            )
        } else {
            AmountField(
                value = balance,
                onValueChange = { balance = it },
                label = "Saldo inicial",
                testTag = "balance_field",
                errorMessage = editing.balanceError,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ChipPicker(
                label = "Moneda",
                errorMessage = editing.currencyError,
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
        }
        Spacer(modifier = Modifier.height(16.dp))
        ChipPicker(
            label = "Tipo",
            errorMessage = editing.typeError,
            errorTestTag = "type_field_error",
        ) {
            for (type in AccountType.entries.filter { it != AccountType.CREDIT_CARD }) {
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
            errorMessage = editing.descriptionError,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SaveAction(editing) {
            onSave(name, amountInputToRaw(balance), selectedCurrency, selectedType, description)
        }
        GeneralMessage(editing)
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
private fun LockedBalance(display: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Saldo inicial",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ReadOnlyValue(display, "balance_readonly")
    }
}

@Composable
private fun LockedCurrency(currency: Currency?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Moneda",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ReadOnlyValue(if (currency != null) currencyLabel(currency) else "", "currency_readonly")
    }
}

@Composable
private fun ReadOnlyValue(value: String, testTag: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate100,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = Slate600,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
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
private fun SaveAction(
    editing: EditAccountUiState.Editing,
    onSave: () -> Unit,
) {
    if (editing.isSaving) {
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
            onClick = onSave,
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
                text = "Guardar cambios",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun GeneralMessage(editing: EditAccountUiState.Editing) {
    if (editing.saveError != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = editing.saveError,
            color = ExpenseRed,
            modifier = Modifier.testTag("save_error"),
        )
    }
}

@Composable
private fun EditAccountLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EditAccountMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Cuenta no encontrada",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("not_found_message"),
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
    AccountType.CREDIT_CARD -> "Tarjeta de crédito"
}
