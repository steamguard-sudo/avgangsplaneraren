package com.avgangsplaneraren.app.ui.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.domain.SavedTrip
import com.avgangsplaneraren.app.ui.stringResource

@Composable
fun SavedTripsSection(
    trips: List<SavedTrip>,
    onTripSelected: (SavedTrip) -> Unit,
    onTripDeleted: (SavedTrip) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<SavedTrip?>(null) }

    Column(modifier = modifier) {
        Text(stringResource(R.string.saved_trips_title), style = MaterialTheme.typography.titleMedium)

        if (trips.isEmpty()) {
            Text(
                stringResource(R.string.no_saved_trips),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            trips.forEach { trip ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onTripSelected(trip) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(trip.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${trip.fromPlace} → ${trip.toPlace}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { pendingDelete = trip }) {
                            Text("✕", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_trip_confirm_title)) },
            text = { Text(stringResource(R.string.delete_trip_confirm_text, trip.label)) },
            confirmButton = {
                TextButton(onClick = {
                    onTripDeleted(trip)
                    pendingDelete = null
                }) { Text(stringResource(R.string.button_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.button_cancel)) }
            }
        )
    }
}

@Composable
fun SaveTripDialog(
    defaultLabel: String,
    onConfirm: (label: String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(defaultLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_trip_dialog_title)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.save_trip_label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.ifBlank { defaultLabel }) }
            ) { Text(stringResource(R.string.button_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        }
    )
}
