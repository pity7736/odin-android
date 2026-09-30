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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.Savings
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
import dev.raiseexception.odin.shared.presentation.AccountIcon
import dev.raiseexception.odin.shared.presentation.BottomBarTab
import dev.raiseexception.odin.shared.presentation.CreditCardRow
import dev.raiseexception.odin.shared.presentation.OdinBottomBar
import dev.raiseexception.odin.shared.presentation.capitalizeFirst
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.ui.theme.OrangePrimary
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
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
                moneyAccounts = uiState.moneyAccounts,
                creditCards = uiState.creditCards,
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
    moneyAccounts: List<Account>,
    creditCards: List<CreditCardItem>,
    onAccountSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.padding(horizontal = 20.dp)) {
        item(key = "top_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (moneyAccounts.isNotEmpty()) {
            item(key = "accounts_group") {
                AccountsGroup(
                    title = "Cuentas",
                    headerTag = "accounts_group_header",
                ) {
                    moneyAccounts.forEachIndexed { index, account ->
                        AccountRow(
                            account = account,
                            backgroundColor = alternatingRowBackground(index),
                            onClick = { onAccountSelected(account.id) },
                        )
                    }
                }
            }
        }
        if (creditCards.isNotEmpty()) {
            item(key = "credit_cards_group") {
                AccountsGroup(
                    title = "Tarjetas de crédito",
                    headerTag = "credit_cards_group_header",
                ) {
                    creditCards.forEachIndexed { index, creditCard ->
                        CreditCardRow(
                            name = creditCard.name,
                            debt = creditCard.debt,
                            availableCredit = creditCard.availableCredit,
                            onClick = { onAccountSelected(creditCard.id) },
                            modifier = Modifier.background(alternatingRowBackground(index)),
                        )
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
private fun AccountsGroup(
    title: String,
    headerTag: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Slate900,
            modifier = Modifier
                .padding(bottom = 10.dp)
                .testTag(headerTag),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()
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
        AccountIcon(
            imageVector = when (account.type) {
                AccountType.SAVINGS -> Icons.Outlined.Savings
                AccountType.CASH -> Icons.Filled.Payments
                AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
            },
        )
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

private fun alternatingRowBackground(index: Int): Color = if (index % 2 == 0) Color.White else Slate50
