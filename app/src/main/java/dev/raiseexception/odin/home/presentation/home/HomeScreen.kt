@file:Suppress("TooManyFunctions", "LongMethod")

package dev.raiseexception.odin.home.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.home.application.usecase.RecentTransaction
import dev.raiseexception.odin.shared.presentation.BottomBarTab
import dev.raiseexception.odin.shared.presentation.OdinBottomBar
import dev.raiseexception.odin.shared.presentation.capitalizeFirst
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.ui.theme.ExpenseBadge
import dev.raiseexception.odin.ui.theme.ExpenseDark
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.IncomeBadge
import dev.raiseexception.odin.ui.theme.IncomeGreen
import dev.raiseexception.odin.ui.theme.OrangePrimary
import dev.raiseexception.odin.ui.theme.Slate100
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import dev.raiseexception.odin.ui.theme.SoraFamily
import kotlinx.coroutines.flow.Flow

@Suppress("LongParameterList")
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    navigationEvent: Flow<HomeNavigationTarget>,
    onAccountSelected: (String) -> Unit,
    onTransactionSelected: (String) -> Unit,
    onCreateAccountSelected: () -> Unit,
    onNavigateToAccountDetail: (String) -> Unit,
    onNavigateToTransactionDetail: (String) -> Unit,
    onNavigateToAccountCreate: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { target ->
            when (target) {
                is HomeNavigationTarget.AccountDetail -> onNavigateToAccountDetail(target.accountId)
                is HomeNavigationTarget.TransactionDetail -> onNavigateToTransactionDetail(target.transactionId)
                is HomeNavigationTarget.AccountCreate -> onNavigateToAccountCreate()
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            OdinBottomBar(
                selectedTab = BottomBarTab.HOME,
                onNavigateToHome = {},
                onNavigateToAccounts = onNavigateToAccounts,
                onNavigateToCategories = onNavigateToCategories,
            )
        }
    ) { innerPadding ->
        when (uiState) {
            is HomeUiState.Loading -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is HomeUiState.Empty -> EmptyContent(
                onCreateAccount = onCreateAccountSelected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is HomeUiState.Content -> SummaryContent(
                totalBalances = uiState.totalBalances,
                accounts = uiState.accounts,
                hasMoreAccounts = uiState.hasMoreAccounts,
                recentTransactions = uiState.recentTransactions,
                onAccountSelected = onAccountSelected,
                onTransactionSelected = onTransactionSelected,
                onSeeAllAccounts = onNavigateToAccounts,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is HomeUiState.Error -> ErrorContent(
                message = uiState.message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyContent(onCreateAccount: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(Slate100),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CreditCard,
                contentDescription = null,
                tint = Slate400,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "SALDO TOTAL",
            style = MaterialTheme.typography.labelLarge,
            color = Slate400,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$0",
            style = MaterialTheme.typography.displayLarge,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Bold,
            color = Slate900,
            modifier = Modifier.testTag("total_balance_zero"),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tienes cuentas registradas",
            style = MaterialTheme.typography.bodyLarge,
            color = Slate500,
            modifier = Modifier.testTag("empty_accounts_message"),
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onCreateAccount,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OrangePrimary,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
            modifier = Modifier
                .height(52.dp)
                .testTag("create_first_account_action"),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Crear mi primera cuenta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SummaryContent(
    totalBalances: List<Money>,
    accounts: List<Account>,
    hasMoreAccounts: Boolean,
    recentTransactions: List<RecentTransaction>,
    onAccountSelected: (String) -> Unit,
    onTransactionSelected: (String) -> Unit,
    onSeeAllAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.padding(horizontal = 20.dp)) {
        item(key = "total_balances") {
            BalanceCard(
                totalBalances = totalBalances,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
            )
        }
        item(key = "accounts_header") {
            AccountsSectionHeader(
                hasMoreAccounts = hasMoreAccounts,
                onSeeAllAccounts = onSeeAllAccounts,
            )
        }
        item(key = "accounts_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(
                        Modifier.background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                        )
                    ),
            ) {
                accounts.forEachIndexed { index, account ->
                    AccountRow(account = account, onClick = { onAccountSelected(account.id) })
                    if (index < accounts.lastIndex) {
                        HorizontalDivider(
                            color = Slate100,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
        item(key = "transactions_header") {
            Text(
                text = "Actividad reciente",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SoraFamily,
                color = Slate900,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )
        }
        if (recentTransactions.isEmpty()) {
            item(key = "no_transactions") {
                EmptyTransactionsMessage()
            }
        } else {
            item(key = "transactions_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    recentTransactions.forEachIndexed { index, recentTransaction ->
                        RecentTransactionRow(
                            recentTransaction = recentTransaction,
                            onClick = { onTransactionSelected(recentTransaction.transaction.id) },
                        )
                        if (index < recentTransactions.lastIndex) {
                            HorizontalDivider(
                                color = Slate100,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BalanceCard(totalBalances: List<Money>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Slate800)
            .padding(24.dp),
    ) {
        Text(
            text = "SALDO TOTAL",
            style = MaterialTheme.typography.labelLarge,
            color = Slate400,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        totalBalances.forEach { balance ->
            Text(
                text = formatMoney(balance),
                style = MaterialTheme.typography.displayLarge,
                color = Slate50,
                modifier = Modifier.testTag("total_balance_${balance.currency.name}"),
            )
            Text(
                text = balance.currency.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Slate500,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AccountsSectionHeader(
    hasMoreAccounts: Boolean,
    onSeeAllAccounts: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cuentas",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = SoraFamily,
            color = Slate900,
        )
        if (hasMoreAccounts) {
            TextButton(
                onClick = onSeeAllAccounts,
                modifier = Modifier.testTag("see_all_accounts_action"),
            ) {
                Text(
                    text = "Ver todas",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = OrangePrimary,
                )
            }
        }
    }
}

@Composable
private fun EmptyTransactionsMessage() {
    Text(
        text = "No hay movimientos recientes",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .testTag("empty_transactions_message"),
    )
}

@Composable
private fun AccountRow(account: Account, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Slate100),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (account.type) {
                    AccountType.SAVINGS -> Icons.Outlined.CreditCard
                    AccountType.CASH -> Icons.Filled.Payments
                },
                contentDescription = null,
                tint = Slate600,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = capitalizeFirst(account.name),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Slate800,
            )
            Text(
                text = accountTypeLabel(account.type),
                style = MaterialTheme.typography.bodySmall,
                color = Slate400,
            )
        }
        Text(
            text = formatMoney(account.balance),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = Slate800,
        )
    }
}

@Composable
private fun RecentTransactionRow(recentTransaction: RecentTransaction, onClick: () -> Unit) {
    val isIncome = recentTransaction.transaction is Income
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
                    recentTransaction.transaction.description.ifBlank {
                        if (isIncome) "Ingreso" else "Gasto"
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Slate800,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = capitalizeFirst(recentTransaction.accountName),
                style = MaterialTheme.typography.bodySmall,
                color = Slate400,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$amountPrefix${formatMoney(recentTransaction.transaction.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
            )
            Text(
                text = formatDate(recentTransaction.transaction.date),
                style = MaterialTheme.typography.labelSmall,
                color = Slate400,
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.SAVINGS -> "Ahorro"
    AccountType.CASH -> "Efectivo"
}

private fun formatDate(date: kotlinx.datetime.LocalDate): String {
    val months = arrayOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )
    return "${date.dayOfMonth} ${months[date.monthNumber - 1]} ${date.year}"
}
