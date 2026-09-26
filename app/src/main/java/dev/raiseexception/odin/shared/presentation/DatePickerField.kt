package dev.raiseexception.odin.shared.presentation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.Slate200
import dev.raiseexception.odin.ui.theme.Slate50
import dev.raiseexception.odin.ui.theme.Slate500
import dev.raiseexception.odin.ui.theme.Slate800
import dev.raiseexception.odin.ui.theme.Slate900
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    errorMessage: String?,
    minDate: LocalDate?,
) {
    var showPicker by remember { mutableStateOf(false) }
    val todayMillis = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            .toEpochDays().toLong() * MILLIS_PER_DAY
    }
    val minDateMillis = remember(minDate) {
        minDate?.toEpochDays()?.toLong()?.times(MILLIS_PER_DAY)
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateMillis(selectedDate) ?: todayMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val aboveMin = minDateMillis == null || utcTimeMillis >= minDateMillis
                return utcTimeMillis <= todayMillis && aboveMin
            }
        },
    )
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is PressInteraction.Release) showPicker = true
        }
    }
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateSelected(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date.toString())
                    }
                    showPicker = false
                }) { Text("OK", color = Slate800) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar", color = Slate500) }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color.White,
            ),
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    titleContentColor = Slate900,
                    headlineContentColor = Slate900,
                    weekdayContentColor = Slate500,
                    navigationContentColor = Slate800,
                    yearContentColor = Slate800,
                    selectedDayContainerColor = Slate800,
                    selectedDayContentColor = Slate50,
                    selectedYearContainerColor = Slate800,
                    selectedYearContentColor = Slate50,
                    todayContentColor = Slate800,
                    todayDateBorderColor = Slate800,
                    dayContentColor = Slate900,
                ),
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Fecha",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Slate800,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = selectedDate,
            onValueChange = {},
            singleLine = true,
            readOnly = true,
            isError = errorMessage != null,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
                focusedBorderColor = if (errorMessage != null) ExpenseRed else Slate200,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("date_field"),
        )
        FieldError(errorMessage, "date_field_error")
    }
}

private fun dateMillis(isoDate: String): Long? = try {
    LocalDate.parse(isoDate).toEpochDays().toLong() * MILLIS_PER_DAY
} catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
    null
}

private const val MILLIS_PER_DAY = 86_400_000L
