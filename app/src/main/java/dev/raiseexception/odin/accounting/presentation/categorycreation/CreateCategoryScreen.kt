package dev.raiseexception.odin.accounting.presentation.categorycreation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

@Composable
fun CreateCategoryScreen(
    uiState: CreateCategoryUiState,
    onCreate: (String, CategoryType?, String, String?) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    onCreateSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { onCreateSuccess() }
    }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<CategoryType?>(null) }
    val validation = uiState as? CreateCategoryUiState.ValidationError
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Nueva categoría",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("create_category_title")
        )
        LabeledField(name, { name = it }, "Nombre", "name_field", validation?.nameError)
        TypePicker(selectedType, { selectedType = it }, validation?.typeError)
        LabeledField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción (opcional)",
            testTag = "description_field",
            errorMessage = validation?.descriptionError
        )
        LabeledField(
            value = color,
            onValueChange = { color = it },
            label = "Color (opcional, p. ej. #E57373)",
            testTag = "color_field",
            errorMessage = validation?.colorError
        )
        CreateAction(uiState) { onCreate(name, selectedType, description, color.ifBlank { null }) }
        GeneralMessage(uiState)
    }
}

@Composable
private fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = errorMessage != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
        FieldError(errorMessage, "${testTag}_error")
    }
}

@Composable
private fun TypePicker(
    selectedType: CategoryType?,
    onSelect: (CategoryType) -> Unit,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("type_picker")
    ) {
        Text(text = "Tipo", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in CategoryType.entries) {
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onSelect(type) },
                    label = { Text(typeLabel(type)) },
                    modifier = Modifier.testTag("type_option_${type.name}")
                )
            }
        }
        FieldError(errorMessage, "type_field_error")
    }
}

@Composable
private fun FieldError(errorMessage: String?, testTag: String) {
    if (errorMessage != null) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun CreateAction(
    uiState: CreateCategoryUiState,
    onCreate: () -> Unit
) {
    when (uiState) {
        is CreateCategoryUiState.Loading -> CircularProgressIndicator(
            modifier = Modifier.testTag("loading_indicator")
        )
        else -> Button(
            onClick = onCreate,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_button")
        ) {
            Text("Crear categoría")
        }
    }
}

@Composable
private fun GeneralMessage(uiState: CreateCategoryUiState) {
    if (uiState is CreateCategoryUiState.Error) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("error_message")
        )
    }
}

private fun typeLabel(type: CategoryType): String = when (type) {
    CategoryType.INCOME -> "Ingreso"
    CategoryType.EXPENSE -> "Gasto"
}
