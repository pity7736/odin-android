package dev.raiseexception.odin.home.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Inicio",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.testTag("home_title")
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onOpenAccounts,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("open_accounts_action")
        ) {
            Text("Mis cuentas")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onOpenCategories,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("open_categories_action")
        ) {
            Text("Categorías")
        }
    }
}
