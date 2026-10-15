package dev.raiseexception.odin.shared.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.raiseexception.odin.ui.theme.ExpenseBadge
import dev.raiseexception.odin.ui.theme.ExpenseDark
import dev.raiseexception.odin.ui.theme.Slate100
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import kotlinx.coroutines.flow.Flow

const val UNEXPECTED_FAILURE_MESSAGE = "Ocurrió un error inesperado."

private val dialogLook = UnexpectedFailureLook(
    iconSize = 48.dp,
    iconBackground = ExpenseBadge,
    iconTint = ExpenseDark,
    actionLabel = "Aceptar",
    actionTag = "unexpected_failure_dismiss",
    fillsWidth = true,
)

private val retryLook = UnexpectedFailureLook(
    iconSize = 56.dp,
    iconBackground = Slate100,
    iconTint = Slate600,
    actionLabel = "Reintentar",
    actionTag = "unexpected_failure_retry",
    fillsWidth = false,
)

@Composable
fun UnexpectedFailureDialogHost(unexpectedFailureEvent: Flow<Unit>) {
    var showsDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(unexpectedFailureEvent) {
        unexpectedFailureEvent.collect { showsDialog = true }
    }
    if (showsDialog) {
        UnexpectedFailureDialog(onDismiss = { showsDialog = false })
    }
}

@Composable
fun UnexpectedFailureDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PlatformDimRemoval()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Slate900.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            UnexpectedFailureMessage(
                look = dialogLook,
                onAction = onDismiss,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 312.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(24.dp)
                    .testTag("unexpected_failure_dialog"),
            )
        }
    }
}

@Composable
fun UnexpectedFailureContent(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    UnexpectedFailureMessage(
        look = retryLook,
        onAction = onRetry,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .testTag("unexpected_failure_content"),
    )
}

@Composable
private fun UnexpectedFailureMessage(look: UnexpectedFailureLook, onAction: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(look.iconSize)
                .background(look.iconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Outlined.ErrorOutline, contentDescription = null, tint = look.iconTint)
        }
        Text(
            text = UNEXPECTED_FAILURE_MESSAGE,
            style = MaterialTheme.typography.bodyLarge,
            color = Slate800,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onAction,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = if (look.fillsWidth) ButtonDefaults.ContentPadding else PaddingValues(horizontal = 28.dp),
            modifier = (if (look.fillsWidth) Modifier.fillMaxWidth() else Modifier)
                .height(48.dp)
                .testTag(look.actionTag),
        ) {
            Text(text = look.actionLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PlatformDimRemoval() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.setDimAmount(0f)
    }
}

private class UnexpectedFailureLook(
    val iconSize: Dp,
    val iconBackground: Color,
    val iconTint: Color,
    val actionLabel: String,
    val actionTag: String,
    val fillsWidth: Boolean,
)
