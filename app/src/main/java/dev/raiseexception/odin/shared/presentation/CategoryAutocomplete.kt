@file:Suppress("LongMethod")

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
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate800

@Composable
fun CategoryAutocomplete(
    categories: List<Category>,
    categoryText: String,
    onCategoryTextChange: (String) -> Unit,
    onSuggestionSelected: (Category) -> Unit,
    errorMessage: String?,
) {
    val suggestions = categories.filter { it.name.contains(categoryText.trim(), ignoreCase = true) }
    var isFocused by remember { mutableStateOf(false) }
    var justSelected by remember { mutableStateOf(false) }
    val showMenu = isFocused && !justSelected && suggestions.isNotEmpty()
    LaunchedEffect(errorMessage) { justSelected = false }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Categoría",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = categoryText,
                onValueChange = { text ->
                    onCategoryTextChange(text)
                    justSelected = false
                },
                singleLine = true,
                isError = errorMessage != null,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                    focusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_field")
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (state.isFocused) justSelected = false
                    },
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { justSelected = true },
                properties = PopupProperties(focusable = false),
                modifier = Modifier.background(Color.White),
            ) {
                suggestions.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = capitalizeFirst(category.name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate800,
                            )
                        },
                        onClick = {
                            onSuggestionSelected(category)
                            justSelected = true
                        },
                        modifier = Modifier.testTag("category_option_${category.id}"),
                    )
                }
            }
        }
        FieldError(errorMessage, "category_field_error")
    }
}

fun resolveCategoryInput(
    categoryText: String,
    selectedCategoryId: String,
    categories: List<Category>,
): CategoryInput {
    if (selectedCategoryId.isNotBlank()) {
        val match = categories.firstOrNull { it.id == selectedCategoryId }
        if (match != null) return CategoryInput.Existing(match.id)
    }
    val exactMatch = categories.firstOrNull { it.name.equals(categoryText.trim(), ignoreCase = true) }
    if (exactMatch != null) return CategoryInput.Existing(exactMatch.id)
    return CategoryInput.New(categoryText.trim())
}
