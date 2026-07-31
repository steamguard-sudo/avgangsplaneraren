package com.avgangsplaneraren.app.ui.planner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.avgangsplaneraren.app.ui.stringResource
import com.avgangsplaneraren.app.R

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
                if (hours == 0 && minutes == 0) {
                    stringResource(R.string.duration_none_selected)
                } else {
                    stringResource(R.string.duration_format, hours, minutes.toString().padStart(2, '0'))
                }
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
                }) { Text(stringResource(R.string.button_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.button_cancel)) }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}
