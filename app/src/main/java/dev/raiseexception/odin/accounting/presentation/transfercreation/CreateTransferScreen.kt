@file:Suppress("TooManyFunctions", "LongMethod", "LongParameterList")

package dev.raiseexception.odin.accounting.presentation.transfercreation

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.shared.presentation.AmountField
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
fun CreateTransferScreen(
    uiState: CreateTransferUiState,
    onSave: (String, String, String, String) -> Unit,
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
        is CreateTransferUiState.Loading -> LoadingContent(modifier)
        is CreateTransferUiState.Error -> ErrorContent(message = uiState.message, modifier = modifier)
        else -> TransferForm(
            accounts = when (uiState) {
                is CreateTransferUiState.Idle -> uiState.accounts
                is CreateTransferUiState.ValidationError -> uiState.accounts
                else -> emptyList()
            },
            selectedSourceAccountId = when (uiState) {
                is CreateTransferUiState.Idle -> uiState.selectedSourceAccountId
                else -> ""
            },
            validation = uiState as? CreateTransferUiState.ValidationError,
            isSaving = uiState is CreateTransferUiState.Saving,
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
private fun TransferForm(
    accounts: List<Account>,
    selectedSourceAccountId: String,
    validation: CreateTransferUiState.ValidationError?,
    isSaving: Boolean,
    onSave: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sourceAccountId by rememberSaveable { mutableStateOf(selectedSourceAccountId) }
    var destinationAccountId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var rawDate by rememberSaveable {
        mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString())
    }
    val destinationAccounts = accounts.filter { it.id != sourceAccountId }
    val sourceAccount = accounts.firstOrNull { it.id == sourceAccountId }
    val destinationAccount = accounts.firstOrNull { it.id == destinationAccountId }
    val transferMinDate = transferMinDate(sourceAccount, destinationAccount)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Nueva transferencia",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            color = Slate900,
            modifier = Modifier.testTag("create_transfer_title"),
        )
        Spacer(modifier = Modifier.height(28.dp))
        AccountDropdown(
            accounts = accounts,
            selectedAccountId = sourceAccountId,
            onAccountSelected = {
                sourceAccountId = it
                if (destinationAccountId == it) destinationAccountId = ""
            },
            label = "Cuenta origen",
            testTagPrefix = "source_account",
            errorMessage = validation?.sourceAccountError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        AccountDropdown(
            accounts = destinationAccounts,
            selectedAccountId = destinationAccountId,
            onAccountSelected = { destinationAccountId = it },
            label = "Cuenta destino",
            testTagPrefix = "destination_account",
            errorMessage = validation?.destinationAccountError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        AmountField(
            value = amount,
            onValueChange = { amount = it },
            label = "Monto",
            testTag = "amount_field",
            errorMessage = validation?.amountError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        DatePickerField(rawDate, { rawDate = it }, validation?.dateError, transferMinDate)
        Spacer(modifier = Modifier.height(24.dp))
        when {
            isSaving -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> Button(
                onClick = { onSave(sourceAccountId, destinationAccountId, amount, rawDate) },
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
                    text = "Transferir",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    accounts: List<Account>,
    selectedAccountId: String,
    onAccountSelected: (String) -> Unit,
    label: String,
    testTagPrefix: String,
    errorMessage: String?,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: ""
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                isError = errorMessage != null,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                    focusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .testTag("${testTagPrefix}_field"),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = Color.White,
            ) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate800,
                            )
                        },
                        onClick = {
                            onAccountSelected(account.id)
                            expanded = false
                        },
                        modifier = Modifier.testTag("${testTagPrefix}_option_${account.id}"),
                    )
                }
            }
        }
        FieldError(errorMessage, "${testTagPrefix}_field_error")
    }
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

private fun transferMinDate(sourceAccount: Account?, destinationAccount: Account?): LocalDate? {
    val sourceDate = sourceAccount?.createdAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
    val destinationDate = destinationAccount?.createdAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
    return when {
        sourceDate != null && destinationDate != null -> maxOf(sourceDate, destinationDate)
        sourceDate != null -> sourceDate
        destinationDate != null -> destinationDate
        else -> null
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
