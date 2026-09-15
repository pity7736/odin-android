package dev.raiseexception.odin.accounting.presentation.transactiondetail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.raiseexception.odin.shared.presentation.BottomBarTab
import dev.raiseexception.odin.shared.presentation.OdinBottomBar
import dev.raiseexception.odin.ui.theme.ExpenseBadge
import dev.raiseexception.odin.ui.theme.ExpenseDark
import dev.raiseexception.odin.ui.theme.IncomeBadge
import dev.raiseexception.odin.ui.theme.IncomeGreen
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.SoraFamily

@Suppress("LongParameterList")
@Composable
fun TransactionDetailScreen(
    uiState: TransactionDetailUiState,
    onNavigateToHome: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            OdinBottomBar(
                selectedTab = BottomBarTab.HOME,
                onNavigateToHome = onNavigateToHome,
                onNavigateToAccounts = onNavigateToAccounts,
                onNavigateToCategories = onNavigateToCategories,
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is TransactionDetailUiState.Loading -> TransactionDetailLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is TransactionDetailUiState.Content -> TransactionDetailContent(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is TransactionDetailUiState.NotFound -> TransactionDetailMessage(
                message = "Transacción no encontrada",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is TransactionDetailUiState.Error -> TransactionDetailMessage(
                message = uiState.message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun TransactionDetailLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("loading_indicator"),
        )
    }
}

@Composable
private fun TransactionDetailContent(uiState: TransactionDetailUiState.Content, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        TransactionHeaderCard(uiState = uiState)
        Spacer(modifier = Modifier.height(16.dp))
        TransactionInfoSection(uiState = uiState)
    }
}

@Composable
private fun TransactionHeaderCard(uiState: TransactionDetailUiState.Content, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Slate800)
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransactionTypeIcon(isIncome = uiState.isIncome)
            Text(
                text = uiState.formattedAmount,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = SoraFamily,
                color = Slate50,
                modifier = Modifier.testTag("amount_text"),
            )
        }
        Text(
            text = if (uiState.isIncome) "Ingreso" else "Gasto",
            style = MaterialTheme.typography.labelLarge,
            color = Slate400,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TransactionTypeIcon(isIncome: Boolean, modifier: Modifier = Modifier) {
    val backgroundColor = if (isIncome) IncomeBadge else ExpenseBadge
    val iconTint = if (isIncome) IncomeGreen else ExpenseDark
    val icon = if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    val contentDescription = if (isIncome) "Ingreso" else "Gasto"
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .testTag("type_icon"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TransactionInfoSection(uiState: TransactionDetailUiState.Content, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        InfoField(label = "FECHA", value = uiState.formattedDate)
        Spacer(modifier = Modifier.height(16.dp))
        InfoField(label = "CATEGORÍA", value = uiState.categoryName)
        Spacer(modifier = Modifier.height(16.dp))
        InfoField(label = "CUENTA", value = uiState.accountName)
        if (uiState.description.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            InfoField(
                label = "DESCRIPCIÓN",
                value = uiState.description,
                testTag = "description_text",
            )
        }
    }
}

@Composable
private fun InfoField(label: String, value: String, testTag: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Slate400,
            letterSpacing = 0.5.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = Slate600,
            modifier = Modifier
                .padding(top = 4.dp)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        )
    }
}

@Composable
private fun TransactionDetailMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
