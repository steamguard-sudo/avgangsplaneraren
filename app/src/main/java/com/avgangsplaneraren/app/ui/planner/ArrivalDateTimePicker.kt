package com.avgangsplaneraren.app.ui.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.avgangsplaneraren.app.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Väljare för önskad ankomsttid: en knapp som visar nuvarande val, och som
 * öppnar först en datumväljare och sedan en tidväljare (Material 3) när man
 * trycker på den. Ersätter den tidigare hårdkodade "6 timmar från nu".
 *
 * Datumtexten formateras enligt appens aktuella språk (via [LanguageSelector])
 * istället för att alltid vara på svenska, så t.ex. månadsnamn följer med
 * när användaren byter till engelska/tyska.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalDateTimePicker(
    value: LocalDateTime,
    onValueChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val locale = LocalConfiguration.current.locales[0]
    val displayFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEEE d MMMM 'kl.' HH:mm", locale)
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

    if (showDatePicker) {
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
                        // Compose DatePicker jobbar i UTC-epokmillisekunder för
                        // "bara ett datum", oberoende av enhetens tidszon.
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
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
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
