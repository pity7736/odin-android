@file:Suppress("LongMethod", "LongParameterList")

package dev.raiseexception.odin.shared.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate400
import dev.raiseexception.odin.ui.theme.Slate800

@Composable
fun TagField(
    selection: TagSelection,
    suggestions: List<Tag>,
    isEnabled: Boolean,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onSuggestionPicked: (Tag) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }
    val isInputEnabled = isEnabled && !selection.isAtLimit
    val showMenu = isFocused && !isDismissed && isInputEnabled && suggestions.isNotEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Etiquetas",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        TagChips(selection = selection, isEnabled = isEnabled, onRemove = onRemove)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selection.text,
                onValueChange = { text ->
                    onTextChange(text)
                    isDismissed = false
                },
                enabled = isInputEnabled,
                singleLine = true,
                isError = selection.error != null,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = if (selection.error != null) ExpenseRed else Slate200,
                    focusedBorderColor = if (selection.error != null) ExpenseRed else Slate200,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tags_field")
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (state.isFocused) isDismissed = false
                    },
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { isDismissed = true },
                properties = PopupProperties(focusable = false),
                modifier = Modifier.background(Color.White),
            ) {
                suggestions.forEach { tag ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate800,
                            )
                        },
                        onClick = { onSuggestionPicked(tag) },
                        modifier = Modifier.testTag("tag_option_${tag.id}"),
                    )
                }
            }
        }
        if (selection.error != null) {
            FieldError(selection.error, "tags_field_error")
        } else {
            Text(
                text = Expense.TAG_LIMIT_MESSAGE,
                color = Slate400,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("tags_field_hint"),
            )
        }
    }
}

@Composable
private fun TagChips(selection: TagSelection, isEnabled: Boolean, onRemove: (Int) -> Unit) {
    if (selection.selected.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("tag_chips"),
        ) {
            selection.selected.forEachIndexed { index, selectedTag ->
                InputChip(
                    selected = false,
                    onClick = {},
                    enabled = isEnabled,
                    label = { Text(text = selectedTag.name) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onRemove(index) },
                            enabled = isEnabled,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("tag_remove_$index"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Quitar ${selectedTag.name}",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    modifier = Modifier.testTag("tag_chip_$index"),
                )
            }
        }
    }
}
