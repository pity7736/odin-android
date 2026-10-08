package dev.raiseexception.odin.shared.presentation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.raiseexception.odin.ui.theme.Slate100
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
fun DateRangePickerField(
    start: LocalDate,
    end: LocalDate,
    onRangeSelected: (LocalDate, LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val todayMillis = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            .toEpochDays().toLong() * MILLIS_PER_DAY
    }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is PressInteraction.Release) showPicker = true
        }
    }
    if (showPicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start.toEpochDays().toLong() * MILLIS_PER_DAY,
            initialSelectedEndDateMillis = end.toEpochDays().toLong() * MILLIS_PER_DAY,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            onRangeSelected(localDateOf(startMillis), localDateOf(endMillis))
                            showPicker = false
                        }
                    },
                    enabled = dateRangePickerState.selectedEndDateMillis != null,
                ) { Text("OK", color = Slate800) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar", color = Slate500) }
            },
            colors = DatePickerDefaults.colors(containerColor = Color.White),
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    titleContentColor = Slate900,
                    headlineContentColor = Slate900,
                    weekdayContentColor = Slate500,
                    navigationContentColor = Slate800,
                    yearContentColor = Slate800,
                    selectedDayContainerColor = Slate800,
                    selectedDayContentColor = Slate50,
                    dayInSelectionRangeContainerColor = Slate100,
                    dayInSelectionRangeContentColor = Slate900,
                    todayContentColor = Slate800,
                    todayDateBorderColor = Slate800,
                    dayContentColor = Slate900,
                ),
                modifier = Modifier.height(RANGE_PICKER_HEIGHT.dp),
            )
        }
    }
    OutlinedTextField(
        value = "${formatShortSpanishDate(start)} – ${formatShortSpanishDate(end)}",
        onValueChange = {},
        singleLine = true,
        readOnly = true,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Slate800),
        leadingIcon = {
            Icon(imageVector = Icons.Outlined.CalendarMonth, contentDescription = null, tint = Slate500)
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Slate200,
            focusedBorderColor = Slate200,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("date_range_field"),
    )
}

private fun localDateOf(utcMillis: Long): LocalDate =
    Instant.fromEpochMilliseconds(utcMillis).toLocalDateTime(TimeZone.UTC).date

private const val RANGE_PICKER_HEIGHT = 500
