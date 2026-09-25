@file:Suppress("TooManyFunctions", "LongMethod")

package dev.raiseexception.odin.accounting.presentation.accountslist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.shared.presentation.BottomBarTab
import dev.raiseexception.odin.shared.presentation.OdinBottomBar
import dev.raiseexception.odin.shared.presentation.capitalizeFirst
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.ui.theme.OrangePrimary
import dev.raiseexception.odin.ui.theme.Slate100
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800
import kotlinx.coroutines.flow.Flow

@Suppress("LongParameterList")
@Composable
fun AccountsListScreen(
    uiState: AccountsListUiState,
    navigationEvent: Flow<AccountsListNavigationTarget>,
    onCreateAccount: () -> Unit,
    onAccountSelected: (String) -> Unit,
    onNavigateToAccountDetail: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { target ->
            when (target) {
                is AccountsListNavigationTarget.AccountDetail -> onNavigateToAccountDetail(target.accountId)
            }
        }
    }
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
            FloatingActionButton(
                onClick = onCreateAccount,
                containerColor = OrangePrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("create_account_fab"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Crear cuenta",
                )
            }
        },
    ) { innerPadding ->
        when (uiState) {
            is AccountsListUiState.Loading -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is AccountsListUiState.Empty -> EmptyContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is AccountsListUiState.Content -> AccountsContent(
                accounts = uiState.accounts,
                onAccountSelected = onAccountSelected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is AccountsListUiState.Error -> ErrorContent(
                message = uiState.message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "No hay cuentas registradas",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("empty_accounts_message"),
        )
    }
}

@Composable
private fun AccountsContent(
    accounts: List<Account>,
    onAccountSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.padding(horizontal = 20.dp)) {
        item(key = "top_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item(key = "accounts_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                accounts.forEachIndexed { index, account ->
                    val rowBackground = if (index % 2 == 0) Color.White else Slate50
                    AccountRow(
                        account = account,
                        backgroundColor = rowBackground,
                        onClick = { onAccountSelected(account.id) },
                    )
                }
            }
        }
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AccountRow(account: Account, backgroundColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
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
                    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
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
    AccountType.CREDIT_CARD -> "Tarjeta de crédito"
}
