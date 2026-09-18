package dev.raiseexception.odin.shared.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.ui.theme.ExpenseBadge
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.IncomeBadge
import dev.raiseexception.odin.ui.theme.IncomeGreen
import dev.raiseexception.odin.ui.theme.OrangePrimary
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate800

@Suppress("LongParameterList")
@Composable
fun ExpandableFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    showTransferOption: Boolean,
    onIncomeSelected: () -> Unit,
    onExpenseSelected: () -> Unit,
    onTransferSelected: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IncomeAction(onSelected = onIncomeSelected)
                ExpenseAction(onSelected = onExpenseSelected)
                if (showTransferOption) {
                    TransferAction(onSelected = onTransferSelected)
                }
            }
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = OrangePrimary,
            contentColor = Slate50,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("expandable_fab"),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Cerrar" else "Nuevo",
            )
        }
    }
}

@Composable
private fun IncomeAction(onSelected: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Ingreso",
            style = MaterialTheme.typography.labelLarge,
            color = Slate50,
        )
        Spacer(modifier = Modifier.width(8.dp))
        SmallFloatingActionButton(
            onClick = onSelected,
            containerColor = IncomeBadge,
            contentColor = IncomeGreen,
            modifier = Modifier.testTag("create_income_fab"),
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Ingreso")
        }
    }
}

@Composable
private fun ExpenseAction(onSelected: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Gasto",
            style = MaterialTheme.typography.labelLarge,
            color = Slate50,
        )
        Spacer(modifier = Modifier.width(8.dp))
        SmallFloatingActionButton(
            onClick = onSelected,
            containerColor = ExpenseBadge,
            contentColor = ExpenseRed,
            modifier = Modifier.testTag("create_expense_fab"),
        ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Gasto")
        }
    }
}

@Composable
private fun TransferAction(onSelected: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Transferencia",
            style = MaterialTheme.typography.labelLarge,
            color = Slate50,
        )
        Spacer(modifier = Modifier.width(8.dp))
        SmallFloatingActionButton(
            onClick = onSelected,
            containerColor = Slate200,
            contentColor = Slate800,
            modifier = Modifier.testTag("create_transfer_fab"),
        ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Transferencia")
        }
    }
}
