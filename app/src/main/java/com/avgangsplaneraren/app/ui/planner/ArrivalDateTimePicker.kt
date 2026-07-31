package com.avgangsplaneraren.app.ui.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.AppLanguageState
import com.avgangsplaneraren.app.ui.stringResource
import com.avgangsplaneraren.app.ui.withLocale
import com.avgangsplaneraren.app.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalDateTimePicker(
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val languageTag by AppLanguageState.current
    val displayFormatter = remember(languageTag) {
        DateTimeFormatter.ofPattern("EEEE d MMMM 'kl.' HH:mm", Locale(languageTag))
    }

    Column(modifier = modifier) {
        Text(stringResource(R.string.label_arrival_time), style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value.format(displayFormatter).replaceFirstChar { it.uppercase() })
        }
    }

    val baseContext = LocalContext.current
    val localizedContext = remember(languageTag) { baseContext.withLocale(languageTag) }

    if (showDatePicker) {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedContext.resources.configuration
        ) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = value
                    .atZone(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onValueChange(LocalDateTime.of(newDate, value.toLocalTime()))
                        }
                        showDatePicker = false
                        showTimePicker = true
                    }) { Text(stringResource(R.string.button_next)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.button_cancel)) }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    title = {
                        Text(
                            stringResource(R.string.select_date_title),
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                        )
                    }
                )
            }
        }
    }

    if (showTimePicker) {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedContext.resources.configuration
        ) {
            val timePickerState = rememberTimePickerState(
                initialHour = value.hour,
                initialMinute = value.minute,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        onValueChange(
                            LocalDateTime.of(
                                value.toLocalDate(),
                                LocalTime.of(timePickerState.hour, timePickerState.minute)
                            )
                        )
                        showTimePicker = false
                    }) { Text(stringResource(R.string.button_done)) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.button_cancel)) }
                },
                text = { TimePicker(state = timePickerState) }
            )
        }
    }
}
