package com.zghonnyk.plateau.feature.main.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zghonnyk.plateau.feature.main.R
import com.zghonnyk.ui.theme.spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private enum class PickerStep { None, Date, Time }

@Composable
fun MeasurementDateTimePicker(
    onDismiss: () -> Unit,
    onDateTimeSelected: (Instant) -> Unit
) {
    var step by remember { mutableStateOf(PickerStep.Date) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
        }
    )
    val timePickerState = rememberTimePickerState(is24Hour = true)

    when (step) {
        PickerStep.None -> onDismiss()

        PickerStep.Date -> {
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            step = PickerStep.Time
                        }
                    }) {
                        Text(stringResource(R.string.picker_next))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        PickerStep.Time -> {
            val selectedDateTime = (selectedDate ?: LocalDate.now())
                .atTime(timePickerState.hour, timePickerState.minute)
            val selectedInstant = selectedDateTime.atZone(ZoneId.systemDefault()).toInstant()
            val isFuture = selectedInstant.isAfter(Instant.now())

            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.picker_select_time)) },
                text = {
                    Column {
                        TimePicker(state = timePickerState)
                        if (isFuture) {
                            Text(
                                stringResource(R.string.picker_future_error),
                                modifier = Modifier.padding(top = spacing.small)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (!isFuture) {
                                onDateTimeSelected(selectedInstant)
                                step = PickerStep.None
                            }
                        },
                        enabled = !isFuture
                    ) {
                        Text(stringResource(R.string.picker_done))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) }
                }
            )
        }
    }
}