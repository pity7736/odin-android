package dev.raiseexception.odin.accounting.presentation.transactiondetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.raiseexception.odin.shared.presentation.OdinBottomBar
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureContent
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureDialogHost
import dev.raiseexception.odin.ui.theme.ExpenseBadge
import dev.raiseexception.odin.ui.theme.ExpenseDark
import dev.raiseexception.odin.ui.theme.IncomeBadge
import dev.raiseexception.odin.ui.theme.IncomeGreen
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.SoraFamily
import kotlinx.coroutines.flow.Flow

@Suppress("LongParameterList")
@Composable
fun TransactionDetailScreen(
    uiState: TransactionDetailUiState,
    onNavigateToHome: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToReports: () -> Unit,
    onEditExpense: () -> Unit,
    onEditIncome: () -> Unit,
    onDelete: () -> Unit,
    deletionMessageEvent: Flow<String>,
    unexpectedFailureEvent: Flow<Unit>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDeleting = (uiState as? TransactionDetailUiState.Content)?.isDeleting == true
    BackHandler(enabled = isDeleting) {}
    UnexpectedFailureDialogHost(unexpectedFailureEvent)
    DeletionMessageDialogHost(deletionMessageEvent)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            OdinBottomBar(
                selectedTab = null,
                onNavigateToHome = { if (!isDeleting) onNavigateToHome() },
                onNavigateToAccounts = { if (!isDeleting) onNavigateToAccounts() },
                onNavigateToCategories = { if (!isDeleting) onNavigateToCategories() },
                onNavigateToReports = { if (!isDeleting) onNavigateToReports() },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is TransactionDetailUiState.UnexpectedFailure -> UnexpectedFailureContent(
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is TransactionDetailUiState.Loading -> TransactionDetailLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            is TransactionDetailUiState.Content -> TransactionDetailContent(
                uiState = uiState,
                onEdit = if (uiState.isIncome) onEditIncome else onEditExpense,
                onDelete = onDelete,
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
private fun DeletionMessageDialogHost(deletionMessageEvent: Flow<String>) {
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(deletionMessageEvent) {
        deletionMessageEvent.collect { message = it }
    }
    message?.let { shownMessage ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = {
                TextButton(
                    onClick = { message = null },
                    modifier = Modifier.testTag("deletion_message_dismiss"),
                ) {
                    Text(text = "Entendido")
                }
            },
            text = { Text(text = shownMessage) },
            modifier = Modifier.testTag("deletion_message_dialog"),
        )
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
private fun TransactionDetailContent(
    uiState: TransactionDetailUiState.Content,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showsDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        TransactionHeaderCard(
            uiState = uiState,
            onEdit = onEdit,
            onDeleteRequest = { showsDeleteConfirmation = true },
        )
        Spacer(modifier = Modifier.height(16.dp))
        TransactionInfoSection(uiState = uiState)
    }
    if (showsDeleteConfirmation) {
        DeleteConfirmationDialog(
            isIncome = uiState.isIncome,
            onConfirm = {
                showsDeleteConfirmation = false
                onDelete()
            },
            onCancel = { showsDeleteConfirmation = false },
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(isIncome: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val question = if (isIncome) "¿Eliminar este ingreso?" else "¿Eliminar este gasto?"
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("delete_confirmation_confirm")) {
                Text(text = "Eliminar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("delete_confirmation_cancel")) {
                Text(text = "Cancelar")
            }
        },
        text = { Text(text = "$question Esta acción no se puede deshacer.") },
        modifier = Modifier.testTag("delete_confirmation_dialog"),
    )
}

@Composable
private fun TransactionHeaderCard(
    uiState: TransactionDetailUiState.Content,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            TransactionTypeIcon(isIncome = uiState.isIncome, typeLabel = uiState.typeLabel)
            Text(
                text = uiState.formattedAmount,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = SoraFamily,
                color = Slate50,
                modifier = Modifier
                    .weight(1f)
                    .testTag("amount_text"),
            )
            if (uiState.isEditable) {
                IconButton(
                    onClick = onEdit,
                    enabled = !uiState.isDeleting,
                    modifier = Modifier.testTag("edit_transaction_button"),
                ) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = "Editar", tint = Slate400)
                }
                IconButton(
                    onClick = onDeleteRequest,
                    enabled = !uiState.isDeleting,
                    modifier = Modifier.testTag("delete_transaction_button"),
                ) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Eliminar", tint = Slate400)
                }
            }
        }
        Text(
            text = uiState.typeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = Slate400,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TransactionTypeIcon(isIncome: Boolean, typeLabel: String, modifier: Modifier = Modifier) {
    val backgroundColor = if (isIncome) IncomeBadge else ExpenseBadge
    val iconTint = if (isIncome) IncomeGreen else ExpenseDark
    val icon = if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
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
            contentDescription = typeLabel,
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
        if (uiState.tagNames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            TagsField(tagNames = uiState.tagNames)
        }
    }
}

@Composable
private fun TagsField(tagNames: List<String>) {
    Column {
        Text(
            text = "ETIQUETAS",
            style = MaterialTheme.typography.labelSmall,
            color = Slate400,
            letterSpacing = 0.5.sp,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("tags_chips"),
        ) {
            tagNames.forEachIndexed { index, tagName ->
                Text(
                    text = tagName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600,
                    modifier = Modifier
                        .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("tag_detail_chip_$index"),
                )
            }
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
