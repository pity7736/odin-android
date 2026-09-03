@file:Suppress("TooManyFunctions")

package dev.raiseexception.odin.accounting.presentation.accountdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.raiseexception.odin.accounting.application.usecase.AccountTransaction
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val accountTypeLabels = mapOf(
    AccountType.SAVINGS to "Ahorros",
    AccountType.CASH to "Efectivo"
)

private val spanishMonths = mapOf(
    java.time.Month.JANUARY to "enero",
    java.time.Month.FEBRUARY to "febrero",
    java.time.Month.MARCH to "marzo",
    java.time.Month.APRIL to "abril",
    java.time.Month.MAY to "mayo",
    java.time.Month.JUNE to "junio",
    java.time.Month.JULY to "julio",
    java.time.Month.AUGUST to "agosto",
    java.time.Month.SEPTEMBER to "septiembre",
    java.time.Month.OCTOBER to "octubre",
    java.time.Month.NOVEMBER to "noviembre",
    java.time.Month.DECEMBER to "diciembre"
)

@Suppress("LongParameterList")
@Composable
fun AccountDetailScreen(
    uiState: AccountDetailUiState,
    navigationEvent: Flow<AccountDetailNavigationTarget>,
    onCreateIncome: () -> Unit,
    onCreateExpense: () -> Unit,
    onFilterChanged: (TransactionFilter) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            onResume()
        }
    }
    LaunchedEffect(Unit) {
        navigationEvent.collect { target ->
            when (target) {
                is AccountDetailNavigationTarget.CreateIncome -> onCreateIncome()
                is AccountDetailNavigationTarget.CreateExpense -> onCreateExpense()
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (uiState is AccountDetailUiState.Content) {
                ExpandableFab(
                    onCreateIncome = onCreateIncome,
                    onCreateExpense = onCreateExpense
                )
            }
        }
    ) { innerPadding ->
        when (uiState) {
            is AccountDetailUiState.Loading -> AccountDetailLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is AccountDetailUiState.Content -> AccountDetailContent(
                account = uiState.account,
                transactions = uiState.transactions,
                activeFilter = uiState.activeFilter,
                onFilterChanged = onFilterChanged,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is AccountDetailUiState.NotFound -> AccountDetailMessage(
                message = "Cuenta no encontrada",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is AccountDetailUiState.Error -> AccountDetailMessage(
                message = uiState.message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun AccountDetailLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Suppress("LongParameterList")
@Composable
private fun AccountDetailContent(
    account: Account,
    transactions: List<AccountTransaction>,
    activeFilter: TransactionFilter,
    onFilterChanged: (TransactionFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AccountHeader(account = account, modifier = Modifier.padding(16.dp))
        TransactionFilterRow(
            activeFilter = activeFilter,
            onFilterChanged = onFilterChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        if (transactions.isEmpty()) {
            TransactionEmptyState(
                activeFilter = activeFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            TransactionList(
                transactions = transactions,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AccountHeader(account: Account, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = account.name, style = MaterialTheme.typography.headlineMedium)
        Text(text = accountTypeLabels[account.type] ?: account.type.name)
        Text(
            text = "${account.balance.amount.toPlainString()} ${account.currency.name}",
            modifier = Modifier.testTag("account_balance")
        )
        Text(text = account.description)
        Text(text = formatCreationDate(account.createdAt))
    }
}

@Composable
private fun TransactionFilterRow(
    activeFilter: TransactionFilter,
    onFilterChanged: (TransactionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        FilterChip(
            selected = activeFilter == TransactionFilter.ALL,
            onClick = { onFilterChanged(TransactionFilter.ALL) },
            label = { Text("Todos") },
            modifier = Modifier.padding(end = 8.dp)
        )
        FilterChip(
            selected = activeFilter == TransactionFilter.INCOME,
            onClick = { onFilterChanged(TransactionFilter.INCOME) },
            label = { Text("Ingresos") },
            modifier = Modifier.padding(end = 8.dp)
        )
        FilterChip(
            selected = activeFilter == TransactionFilter.EXPENSE,
            onClick = { onFilterChanged(TransactionFilter.EXPENSE) },
            label = { Text("Gastos") }
        )
    }
}

@Composable
private fun TransactionEmptyState(activeFilter: TransactionFilter, modifier: Modifier = Modifier) {
    val message = when (activeFilter) {
        TransactionFilter.ALL -> "No hay movimientos registrados"
        TransactionFilter.INCOME -> "No hay ingresos registrados"
        TransactionFilter.EXPENSE -> "No hay gastos registrados"
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("empty_transactions_message")
        )
    }
}

@Composable
private fun TransactionList(transactions: List<AccountTransaction>, modifier: Modifier = Modifier) {
    val groupedByDate = transactions.groupBy { it.date }
    LazyColumn(modifier = modifier) {
        groupedByDate.forEach { (date, transactionsForDate) ->
            item(key = "header-$date") {
                DateHeader(
                    date = date,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(transactionsForDate, key = { it.id }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, modifier: Modifier = Modifier) {
    Text(
        text = formatTransactionDate(date),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
private fun TransactionRow(transaction: AccountTransaction, modifier: Modifier = Modifier) {
    val amountColor = when (transaction) {
        is AccountTransaction.IncomeTransaction -> MaterialTheme.colorScheme.primary
        is AccountTransaction.ExpenseTransaction -> MaterialTheme.colorScheme.error
    }
    val amountPrefix = when (transaction) {
        is AccountTransaction.IncomeTransaction -> "+"
        is AccountTransaction.ExpenseTransaction -> "-"
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = transaction.description.ifEmpty {
                    when (transaction) {
                        is AccountTransaction.IncomeTransaction -> "Ingreso"
                        is AccountTransaction.ExpenseTransaction -> "Gasto"
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$amountPrefix${transaction.amount.amount.toPlainString()}",
                style = MaterialTheme.typography.bodyLarge,
                color = amountColor,
                fontWeight = FontWeight.Medium
            )
        }
        val balance = transaction.runningBalance
        if (balance != null) {
            Text(
                text = "Saldo: ${balance.amount.toPlainString()} ${balance.currency.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExpandableFab(onCreateIncome: () -> Unit, onCreateExpense: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ingreso", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            onCreateIncome()
                        },
                        modifier = Modifier.testTag("create_income_fab")
                    ) {
                        Text("+")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gasto", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            onCreateExpense()
                        },
                        modifier = Modifier.testTag("create_expense_fab")
                    ) {
                        Text("-")
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("expandable_fab")
        ) {
            Text(if (expanded) "x" else "+")
        }
    }
}

@Composable
private fun AccountDetailMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = message)
    }
}

private fun formatTransactionDate(date: LocalDate): String {
    val currentYear = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).year
    val javaMonth = java.time.Month.of(date.monthNumber)
    val monthName = spanishMonths[javaMonth] ?: date.month.name.lowercase()
    return if (date.year == currentYear) {
        "${date.dayOfMonth} de $monthName"
    } else {
        "${date.dayOfMonth} de $monthName de ${date.year}"
    }
}

private fun formatCreationDate(createdAt: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(Locale("es"))
        .format(createdAt.toJavaInstant().atZone(ZoneId.systemDefault()).toLocalDate())
