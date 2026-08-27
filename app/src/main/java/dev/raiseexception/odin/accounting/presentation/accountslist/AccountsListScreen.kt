package dev.raiseexception.odin.accounting.presentation.accountslist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Account
import kotlinx.coroutines.flow.Flow

@Composable
fun AccountsListScreen(
    uiState: AccountsListUiState,
    navigationEvent: Flow<AccountsListNavigationTarget>,
    onCreateAccount: () -> Unit,
    onNavigateToAccountDetail: (String) -> Unit,
    modifier: Modifier = Modifier
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateAccount,
                modifier = Modifier.testTag("create_account_fab")
            ) {
                Text("+")
            }
        }
    ) { innerPadding ->
        when (uiState) {
            is AccountsListUiState.Loading -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is AccountsListUiState.Empty -> EmptyContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is AccountsListUiState.Content -> AccountsContent(
                accounts = uiState.accounts,
                onAccountSelected = onNavigateToAccountDetail,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            is AccountsListUiState.Error -> ErrorContent(
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
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "No hay cuentas registradas",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("empty_accounts_message")
        )
    }
}

@Composable
private fun AccountsContent(
    accounts: List<Account>,
    onAccountSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(accounts, key = { it.id }) { account ->
            AccountRow(account = account, onClick = { onAccountSelected(account.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun AccountRow(account: Account, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = account.id,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}
