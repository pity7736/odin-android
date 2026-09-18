@file:Suppress("TooManyFunctions", "LongMethod")

package dev.raiseexception.odin.accounting.presentation.accountdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.raiseexception.odin.accounting.application.usecase.AccountTransaction
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter
import dev.raiseexception.odin.shared.presentation.BottomBarTab
import dev.raiseexception.odin.shared.presentation.ExpandableFab
import dev.raiseexception.odin.shared.presentation.OdinBottomBar
import dev.raiseexception.odin.shared.presentation.SPANISH_MONTHS
import dev.raiseexception.odin.shared.presentation.capitalizeFirst
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.ui.theme.ExpenseBadge
import dev.raiseexception.odin.ui.theme.ExpenseDark
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.IncomeBadge
import dev.raiseexception.odin.ui.theme.IncomeGreen
import dev.raiseexception.odin.ui.theme.Slate100
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import dev.raiseexception.odin.ui.theme.SoraFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val accountTypeLabels = mapOf(
    AccountType.SAVINGS to "Ahorros",
    AccountType.CASH to "Efectivo",
)

@Suppress("LongParameterList")
@Composable
fun AccountDetailScreen(
    uiState: AccountDetailUiState,
    navigationEvent: Flow<AccountDetailNavigationTarget>,
    onCreateIncome: () -> Unit,
    onCreateExpense: () -> Unit,
    onCreateTransfer: () -> Unit,
    onNavigateToTransactionDetail: (String) -> Unit,
    onTransactionSelected: (String) -> Unit,
    onFilterChanged: (TransactionFilter) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { target ->
            when (target) {
                is AccountDetailNavigationTarget.CreateIncome -> onCreateIncome()
                is AccountDetailNavigationTarget.CreateExpense -> onCreateExpense()
                is AccountDetailNavigationTarget.CreateTransfer -> onCreateTransfer()
                is AccountDetailNavigationTarget.TransactionDetail ->
                    onNavigateToTransactionDetail(target.transactionId)
            }
        }
    }
    var fabExpanded by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            OdinBottomBar(
                selectedTab = BottomBarTab.ACCOUNTS,
                onNavigateToHome = onNavigateToHome,
                onNavigateToAccounts = onNavigateToAccounts,
                onNavigateToCategories = onNavigateToCategories,
            )
        },
        floatingActionButton = {
            if (uiState is AccountDetailUiState.Content) {
                ExpandableFab(
                    expanded = fabExpanded,
                    onToggle = { fabExpanded = !fabExpanded },
                    showTransferOption = true,
                    onIncomeSelected = {
                        fabExpanded = false
                        onCreateIncome()
                    },
                    onExpenseSelected = {
                        fabExpanded = false
                        onCreateExpense()
                    },
                    onTransferSelected = {
                        fabExpanded = false
                        onCreateTransfer()
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState) {
                is AccountDetailUiState.Loading -> AccountDetailLoading(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                is AccountDetailUiState.Content -> AccountDetailContent(
                    account = uiState.account,
                    transactions = uiState.transactions,
                    activeFilter = uiState.activeFilter,
                    onTransactionSelected = onTransactionSelected,
                    onFilterChanged = onFilterChanged,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                is AccountDetailUiState.NotFound -> AccountDetailMessage(
                    message = "Cuenta no encontrada",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
                is AccountDetailUiState.Error -> AccountDetailMessage(
                    message = uiState.message,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
            if (fabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Slate900.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { fabExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun AccountDetailLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Suppress("LongParameterList")
@Composable
private fun AccountDetailContent(
    account: Account,
    transactions: List<AccountTransaction>,
    activeFilter: TransactionFilter,
    onTransactionSelected: (String) -> Unit,
    onFilterChanged: (TransactionFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedByDate = transactions.groupBy { it.transaction.date }
    LazyColumn(modifier = modifier) {
        item(key = "header") {
            AccountHeaderCard(
                account = account,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item(key = "filters") {
            TransactionFilterRow(
                activeFilter = activeFilter,
                onFilterChanged = onFilterChanged,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (transactions.isEmpty()) {
            item(key = "empty") {
                TransactionEmptyState(
                    activeFilter = activeFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                )
            }
        } else {
            groupedByDate.forEach { (date, transactionsForDate) ->
                item(key = "header-$date") {
                    DateHeader(
                        date = date,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                item(key = "card-$date") {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        transactionsForDate.forEachIndexed { index, transaction ->
                            TransactionRow(
                                transaction = transaction,
                                onClick = { onTransactionSelected(transaction.transaction.id) },
                            )
                            if (index < transactionsForDate.lastIndex) {
                                HorizontalDivider(
                                    color = Slate100,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun AccountHeaderCard(account: Account, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Slate800)
            .padding(20.dp),
    ) {
        Text(
            text = capitalizeFirst(account.name),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = SoraFamily,
            color = Slate50,
        )
        Text(
            text = accountTypeLabels[account.type] ?: account.type.name,
            style = MaterialTheme.typography.labelLarge,
            color = Slate500,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "SALDO",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = formatMoney(account.balance),
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = SoraFamily,
                    color = Slate50,
                    modifier = Modifier.testTag("account_balance"),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "INICIAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = formatMoney(account.initialBalance),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500,
                    modifier = Modifier.testTag("account_initial_balance"),
                )
            }
        }
    }
}

@Composable
private fun TransactionFilterRow(
    activeFilter: TransactionFilter,
    onFilterChanged: (TransactionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChipItem(
            label = "Todos",
            selected = activeFilter == TransactionFilter.ALL,
            onClick = { onFilterChanged(TransactionFilter.ALL) },
        )
        FilterChipItem(
            label = "Ingresos",
            selected = activeFilter == TransactionFilter.INCOME,
            onClick = { onFilterChanged(TransactionFilter.INCOME) },
        )
        FilterChipItem(
            label = "Gastos",
            selected = activeFilter == TransactionFilter.EXPENSE,
            onClick = { onFilterChanged(TransactionFilter.EXPENSE) },
        )
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Slate800 else MaterialTheme.colorScheme.surface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Slate200),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Slate50 else Slate500,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("empty_transactions_message"),
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate, modifier: Modifier = Modifier) {
    Text(
        text = formatTransactionDate(date).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        color = Slate600,
        letterSpacing = 0.5.sp,
        modifier = modifier,
    )
}

@Composable
private fun TransactionRow(transaction: AccountTransaction, onClick: () -> Unit) {
    val isIncome = transaction.transaction is Income
    val iconBackground = if (isIncome) IncomeBadge else ExpenseBadge
    val iconTint = if (isIncome) IncomeGreen else ExpenseDark
    val amountColor = if (isIncome) IncomeGreen else ExpenseRed
    val amountPrefix = if (isIncome) "+" else "-"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = capitalizeFirst(
                    transaction.transaction.description.ifEmpty {
                        if (isIncome) "Ingreso" else "Gasto"
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Slate800,
            )
            if (transaction.runningBalance != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Saldo: ${formatMoney(transaction.runningBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "$amountPrefix${formatMoney(transaction.transaction.amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
        )
    }
}

@Composable
private fun AccountDetailMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTransactionDate(date: LocalDate): String {
    val currentYear = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).year
    val monthName = SPANISH_MONTHS[date.monthNumber - 1]
    return if (date.year == currentYear) {
        "${date.dayOfMonth} de $monthName"
    } else {
        "${date.dayOfMonth} de $monthName de ${date.year}"
    }
}
