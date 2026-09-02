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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val accountTypeLabels = mapOf(
    AccountType.SAVINGS to "Ahorros",
    AccountType.CASH to "Efectivo"
)

@Suppress("LongParameterList")
@Composable
fun AccountDetailScreen(
    uiState: AccountDetailUiState,
    navigationEvent: Flow<AccountDetailNavigationTarget>,
    onCreateIncome: () -> Unit,
    onCreateExpense: () -> Unit,
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

@Composable
private fun AccountDetailContent(account: Account, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
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

private fun formatCreationDate(createdAt: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(Locale("es"))
        .format(createdAt.toJavaInstant().atZone(ZoneId.systemDefault()).toLocalDate())
