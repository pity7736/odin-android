package dev.raiseexception.odin.shared.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate100
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate600
import dev.raiseexception.odin.ui.theme.Slate800

@Composable
fun ReadOnlyAccount(accountName: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Cuenta",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Slate100,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = capitalizeFirst(accountName),
                style = MaterialTheme.typography.bodyLarge,
                color = Slate600,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .testTag("account_readonly"),
            )
        }
    }
}

@Composable
fun SaveAction(isSaving: Boolean, onSave: () -> Unit) {
    Button(
        onClick = onSave,
        enabled = !isSaving,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Slate800,
            contentColor = Slate50,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("save_button"),
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                color = Slate50,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(20.dp)
                    .testTag("loading_indicator"),
            )
        } else {
            Text(
                text = "Guardar cambios",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
fun SaveErrorMessage(saveError: String?) {
    if (saveError != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = saveError,
            color = ExpenseRed,
            modifier = Modifier.testTag("save_error"),
        )
    }
}

@Composable
fun EditFormLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun TransactionNotFound(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Transacción no encontrada",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("not_found_message"),
        )
    }
}
