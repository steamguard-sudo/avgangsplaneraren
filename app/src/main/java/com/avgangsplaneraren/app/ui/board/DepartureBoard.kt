package com.avgangsplaneraren.app.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.domain.DepartureResult
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
fun DepartureBoard(result: DepartureResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("DU MÅSTE AVGÅ SENAST", style = MaterialTheme.typography.labelMedium)
                Text(
                    result.departureTime.format(timeFormatter),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    result.departureTime.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("${result.distanceKm} km", "Sträcka")
                    Stat("${(result.driveMinutes / 60).toInt()} h ${(result.driveMinutes % 60).toInt()} min", "Körtid")
                    Stat("${result.restStops.size}", "Rastplatser")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (result.restStops.isNotEmpty()) {
            Text("Rastplatser på vägen", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            result.restStops.forEach { stop ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${stop.arrivalAtStop.format(timeFormatter)} · ${stop.name}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Ca ${stop.distanceFromStartKm} km in på resan",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge(text = "Bord", active = stop.hasTable)
                            Badge(text = "Bänk", active = stop.hasBench)
                            Badge(text = "Toalett", active = stop.hasToilet)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Badge(text: String, active: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors()
    )
}
