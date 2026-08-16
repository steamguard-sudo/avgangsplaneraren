package com.avgangsplaneraren.app.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.avgangsplaneraren.app.ui.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.domain.ChargingStation

@Composable
fun ChargingStationsSection(
    stations: List<ChargingStation>,
    searchDone: Boolean,
    searchFailed: Boolean = false
) {
    if (!searchDone) return

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.charging_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.charging_source),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (searchFailed) {
            Text(
                stringResource(
                    if (stations.isEmpty()) R.string.charging_failed_all else R.string.charging_failed_partial
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else if (stations.isEmpty()) {
            Text(
                stringResource(R.string.charging_empty),
                style = MaterialTheme.typography.bodySmall
            )
            return
        }

        val feePaidText = stringResource(R.string.fee_paid)
        val feeFreeText = stringResource(R.string.fee_free)
        val feeUnknownText = stringResource(R.string.fee_unknown)

        stations.forEach { station ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(station.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.rest_stop_distance, station.distanceFromStartKm),
                        style = MaterialTheme.typography.bodySmall
                    )
                    DetourText(station.distanceFromRouteKm)

                    station.operator?.let {
                        Text(
                            stringResource(R.string.operator_format, it),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    station.capacity?.let {
                        Text(
                            stringResource(R.string.capacity_format, it),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    val feeText = when (station.hasFee) {
                        true -> feePaidText
                        false -> feeFreeText
                        null -> feeUnknownText
                    }
                    Text(feeText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DetourText(distanceFromRouteKm: Double) {
    val isLikelyDetour = distanceFromRouteKm > 15.0
    Text(
        stringResource(
            if (isLikelyDetour) R.string.detour_warning else R.string.detour_normal,
            distanceFromRouteKm.toInt()
        ),
        style = MaterialTheme.typography.labelSmall,
        color = if (isLikelyDetour) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
