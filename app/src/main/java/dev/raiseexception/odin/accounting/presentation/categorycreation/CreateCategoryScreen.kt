@file:Suppress("TooManyFunctions")

package dev.raiseexception.odin.accounting.presentation.categorycreation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import dev.raiseexception.odin.ui.theme.SoraFamily
import kotlinx.coroutines.flow.Flow

@Composable
fun CreateCategoryScreen(
    uiState: CreateCategoryUiState,
    onCreate: (String, CategoryType?, String, String?) -> Unit,
    navigationEvent: Flow<NavigationTarget>,
    onCreateSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        navigationEvent.collect { onCreateSuccess() }
    }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var selectedColor by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<CategoryType?>(null) }
    val validation = uiState as? CreateCategoryUiState.ValidationError
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Nueva categoría",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            color = Slate900,
            modifier = Modifier.testTag("create_category_title"),
        )
        Spacer(modifier = Modifier.height(28.dp))
        OdinField(
            value = name,
            onValueChange = { name = it },
            label = "Nombre",
            testTag = "name_field",
            errorMessage = validation?.nameError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TypePicker(selectedType, { selectedType = it }, validation?.typeError)
        Spacer(modifier = Modifier.height(16.dp))
        OdinField(
            value = description,
            onValueChange = { description = it },
            label = "Descripción (opcional)",
            testTag = "description_field",
            errorMessage = validation?.descriptionError,
        )
        Spacer(modifier = Modifier.height(16.dp))
        ColorPicker(selectedColor, { selectedColor = it })
        Spacer(modifier = Modifier.height(24.dp))
        CreateAction(uiState) { onCreate(name, selectedType, description, selectedColor) }
        GeneralMessage(uiState)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun OdinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    errorMessage: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = errorMessage != null,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                focusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        )
        FieldError(errorMessage, "${testTag}_error")
    }
}

@Composable
private fun TypePicker(
    selectedType: CategoryType?,
    onSelect: (CategoryType) -> Unit,
    errorMessage: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Tipo",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in CategoryType.entries) {
                FilterChipItem(
                    label = typeLabel(type),
                    selected = selectedType == type,
                    onClick = { onSelect(type) },
                    testTag = "type_option_${type.name}",
                )
            }
        }
        FieldError(errorMessage, "type_field_error")
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Slate800 else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Slate200),
        modifier = Modifier.testTag(testTag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Slate50 else Slate500,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selectedColor: String?, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Color (opcional)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .testTag("color_picker"),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (hex in Category.DEFAULT_PALETTE) {
                ColorSwatch(hex, selectedColor == hex) { onSelect(hex) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
        Color.Gray
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .clickable { onClick() }
            .testTag("color_swatch_$hex"),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun FieldError(errorMessage: String?, testTag: String) {
    if (errorMessage != null) {
        Text(
            text = errorMessage,
            color = ExpenseRed,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag(testTag),
        )
    }
}

@Composable
private fun CreateAction(
    uiState: CreateCategoryUiState,
    onCreate: () -> Unit,
) {
    when (uiState) {
        is CreateCategoryUiState.Loading -> Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("loading_indicator"),
            )
        }
        else -> Button(
            onClick = onCreate,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Slate800,
                contentColor = Slate50,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("create_button"),
        ) {
            Text(
                text = "Crear categoría",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun GeneralMessage(uiState: CreateCategoryUiState) {
    if (uiState is CreateCategoryUiState.Error) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.message,
            color = ExpenseRed,
            modifier = Modifier.testTag("error_message"),
        )
    }
}

private fun typeLabel(type: CategoryType): String = when (type) {
    CategoryType.INCOME -> "Ingreso"
    CategoryType.EXPENSE -> "Gasto"
}
