package dev.raiseexception.odin.shared.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate800

@Suppress("LongMethod")
@Composable
fun AccountAutocomplete(
    accounts: List<Account>,
    selectedAccountId: String?,
    onAccountSelected: (String) -> Unit,
    isError: Boolean,
    errorMessage: String?,
) {
    val selectedName = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: ""
    var searchText by remember { mutableStateOf(selectedName) }
    val suggestions = accounts.filter { it.name.contains(searchText.trim(), ignoreCase = true) }
    var isFocused by remember { mutableStateOf(false) }
    var justSelected by remember { mutableStateOf(false) }
    val showMenu = isFocused && !justSelected && suggestions.isNotEmpty()
    LaunchedEffect(errorMessage) { justSelected = false }
    LaunchedEffect(selectedAccountId) {
        val name = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: ""
        searchText = name
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        AccountLabel()
        AccountSearchField(
            searchText = searchText,
            onSearchTextChange = { text ->
                searchText = text
                justSelected = false
            },
            isError = isError,
            showMenu = showMenu,
            suggestions = suggestions,
            onAccountClick = { account ->
                searchText = account.name
                onAccountSelected(account.id)
                justSelected = true
            },
            onDismiss = { justSelected = true },
            onFocusChange = { focused ->
                isFocused = focused
                if (focused) justSelected = false
            },
        )
        if (errorMessage != null) {
            AccountFieldError(errorMessage)
        }
    }
}

@Composable
private fun AccountLabel() {
    Text(
        text = "Cuenta",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = Slate800,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Suppress("LongParameterList")
@Composable
private fun AccountSearchField(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    isError: Boolean,
    showMenu: Boolean,
    suggestions: List<Account>,
    onAccountClick: (Account) -> Unit,
    onDismiss: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            singleLine = true,
            isError = isError,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = if (isError) ExpenseRed else Slate200,
                focusedBorderColor = if (isError) ExpenseRed else Slate200,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account_field")
                .onFocusChanged { state -> onFocusChange(state.isFocused) },
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = false),
            modifier = Modifier.background(Color.White),
        ) {
            suggestions.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = capitalizeFirst(account.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate800,
                        )
                    },
                    onClick = { onAccountClick(account) },
                    modifier = Modifier.testTag("account_option_${account.id}"),
                )
            }
        }
    }
}

@Composable
private fun AccountFieldError(message: String) {
    Text(
        text = message,
        color = ExpenseRed,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .padding(top = 4.dp)
            .testTag("account_field_error"),
    )
}
