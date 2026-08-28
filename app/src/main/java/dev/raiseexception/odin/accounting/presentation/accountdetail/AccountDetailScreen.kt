package dev.raiseexception.odin.accounting.presentation.accountdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
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

@Composable
fun AccountDetailScreen(
    uiState: AccountDetailUiState,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
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
        Text(text = "${account.initialBalance.amount.toPlainString()} ${account.currency.name}")
        Text(text = account.description)
        Text(text = formatCreationDate(account.createdAt))
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
