@file:Suppress("TooManyFunctions")

package dev.raiseexception.odin.home.presentation.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.home.application.usecase.RecentTransaction
import kotlinx.coroutines.flow.Flow

private val IncomeGreen = Color(0xFF2E7D32)

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
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
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
                is HomeNavigationTarget.AccountDetail -> onNavigateToAccountDetail(target.accountId)
                is HomeNavigationTarget.TransactionDetail -> onNavigateToTransactionDetail(target.transactionId)
                is HomeNavigationTarget.AccountCreate -> onNavigateToAccountCreate()
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(
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
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(onCreateAccount: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Saldo total: $0",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("total_balance_zero")
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tienes cuentas registradas",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("empty_accounts_message")
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onCreateAccount,
            modifier = Modifier.testTag("create_first_account_action")
        ) {
            Text("Crear mi primera cuenta")
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
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item(key = "header") {
            Text(
                text = "Inicio",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .testTag("home_title")
            )
        }
        item(key = "total_balances") {
            TotalBalancesSection(
                totalBalances = totalBalances,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
        item(key = "accounts_header") {
            AccountsSectionHeader(
                hasMoreAccounts = hasMoreAccounts,
                onSeeAllAccounts = onSeeAllAccounts,
            )
        }
        items(accounts, key = { it.id }) { account ->
            AccountRow(account = account, onClick = { onAccountSelected(account.id) })
            HorizontalDivider()
        }
        item(key = "transactions_header") {
            Text(
                text = "Actividad reciente",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }
        if (recentTransactions.isEmpty()) {
            item(key = "no_transactions") {
                EmptyTransactionsMessage()
            }
        } else {
            items(recentTransactions, key = { it.transaction.id }) { recentTransaction ->
                RecentTransactionRow(
                    recentTransaction = recentTransaction,
                    onClick = { onTransactionSelected(recentTransaction.transaction.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AccountsSectionHeader(
    hasMoreAccounts: Boolean,
    onSeeAllAccounts: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cuentas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (hasMoreAccounts) {
            TextButton(
                onClick = onSeeAllAccounts,
                modifier = Modifier.testTag("see_all_accounts_action")
            ) {
                Text("Ver todas")
            }
        }
    }
}

@Composable
private fun EmptyTransactionsMessage() {
    Text(
        text = "No hay movimientos recientes",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .testTag("empty_transactions_message")
    )
}

@Composable
private fun TotalBalancesSection(totalBalances: List<Money>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        totalBalances.forEach { balance ->
            Text(
                text = "${balance.amount.toPlainString()} ${balance.currency.name}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("total_balance_${balance.currency.name}")
            )
        }
    }
}

@Composable
private fun AccountRow(account: Account, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${account.balance.amount.toPlainString()} ${account.currency.name}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RecentTransactionRow(recentTransaction: RecentTransaction, onClick: () -> Unit) {
    val isIncome = recentTransaction.transaction is Income
    val amountColor = if (isIncome) IncomeGreen else MaterialTheme.colorScheme.error
    val amountPrefix = if (isIncome) "+" else "-"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$amountPrefix${recentTransaction.transaction.amount.amount.toPlainString()}",
                style = MaterialTheme.typography.bodyLarge,
                color = amountColor,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = recentTransaction.transaction.date.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = recentTransaction.accountName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
private fun BottomNavigationBar(
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { Text("H") },
            label = { Text("Inicio") },
            modifier = Modifier.testTag("nav_home"),
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToAccounts,
            icon = { Text("C") },
            label = { Text("Cuentas") },
            modifier = Modifier.testTag("nav_accounts"),
        )
        NavigationBarItem(
            selected = false,
            onClick = onNavigateToCategories,
            icon = { Text("K") },
            label = { Text("Categorías") },
            modifier = Modifier.testTag("nav_categories"),
        )
    }
}
