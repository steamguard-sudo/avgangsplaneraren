package com.avgangsplaneraren.app.ui.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Väljare för en tidslängd (inte en tidpunkt) — t.ex. "hur länge blir
 * campingstoppet". Återanvänder Material 3:s TimePicker-hjul, men tolkar
 * resultatet som timmar+minuter istället för en klockslag, vilket är
 * mycket smidigare än att skriva siffror för hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPicker(
    label: String,
    hours: Int,
    minutes: Int,
    onDurationChange: (hours: Int, minutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (hours == 0 && minutes == 0) "Ingen tid vald"
                else "$hours h ${minutes.toString().padStart(2, '0')} min"
            )
        }
    }

    if (showPicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hours,
            initialMinute = minutes,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDurationChange(timePickerState.hour, timePickerState.minute)
                    showPicker = false
                }) { Text("Klart") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Avbryt") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}
