package com.avgangsplaneraren.app.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.avgangsplaneraren.app.AppLanguageState
import com.avgangsplaneraren.app.ui.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.billing.PremiumFeature
import com.avgangsplaneraren.app.billing.PremiumGate
import com.avgangsplaneraren.app.billing.PremiumViewModel
import com.avgangsplaneraren.app.domain.DepartureResult
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DepartureBoard(result: DepartureResult, premiumViewModel: PremiumViewModel) {
    val languageTag by AppLanguageState.current
    val dateFormatter = remember(languageTag) {
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale(languageTag))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Rak-linje-fallbacken slog till: rutten är en grov uppskattning.
                // Varna tydligt högst upp, eftersom sträcka/körtid och alla
                // utplacerade rast-/ladd-/övernattningspunkter då är opålitliga.
                if (result.isEstimatedRoute) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            stringResource(R.string.estimated_route_warning),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Text(stringResource(R.string.board_must_depart), style = MaterialTheme.typography.labelMedium)
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
                    Stat("${result.distanceKm} km", stringResource(R.string.stat_distance))
                    Stat(
                        stringResource(
                            R.string.drive_time_format,
                            (result.driveMinutes / 60).toInt(),
                            (result.driveMinutes % 60).toInt()
                        ),
                        stringResource(R.string.stat_drive_time)
                    )
                    Stat("${result.restStops.size}", stringResource(R.string.stat_rest_stops))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Visa sektionen även när listan är tom, så länge raster faktiskt
        // planerades (plannedBreaks > 0). Annars försvann rastplatserna helt
        // utan ett ord om varför — ingen tom-text, inget fel. En kort resa
        // utan planerade raster (plannedBreaks == 0) hoppar vi däremot över.
        if (result.restStops.isNotEmpty() || result.plannedBreaks > 0) {
            PremiumGate(feature = PremiumFeature.REST_STOPS, viewModel = premiumViewModel) {
                Text(stringResource(R.string.rest_stops_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (result.restStops.isEmpty()) {
                    Text(
                        stringResource(R.string.rest_stops_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    val tableLabel = stringResource(R.string.badge_table)
                    val benchLabel = stringResource(R.string.badge_bench)
                    val toiletLabel = stringResource(R.string.badge_toilet)
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
                                    stringResource(R.string.rest_stop_distance, stop.distanceFromStartKm),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Badge(text = tableLabel, active = stop.hasTable)
                                    Badge(text = benchLabel, active = stop.hasBench)
                                    Badge(text = toiletLabel, active = stop.hasToilet)
                                }
                            }
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
