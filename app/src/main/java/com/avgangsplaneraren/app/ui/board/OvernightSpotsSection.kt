package com.avgangsplaneraren.app.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.avgangsplaneraren.app.ui.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.domain.OvernightSpot

@Composable
fun OvernightSpotsSection(spots: List<OvernightSpot>, searchDone: Boolean, searchFailed: Boolean = false) {
    if (!searchDone) return

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.overnight_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.overnight_source),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (searchFailed) {
            Text(
                stringResource(
                    if (spots.isEmpty()) R.string.overnight_failed_all else R.string.overnight_failed_partial
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else if (spots.isEmpty()) {
            Text(
                stringResource(R.string.overnight_empty),
                style = MaterialTheme.typography.bodySmall
            )
            return
        }

        val caravanSiteLabel = stringResource(R.string.type_caravan_site)
        val campSiteLabel = stringResource(R.string.type_camp_site)
        val feePaidText = stringResource(R.string.fee_paid)
        val feeFreeText = stringResource(R.string.fee_free)
        val feeUnknownText = stringResource(R.string.fee_unknown)
        val caravanLabel = stringResource(R.string.vehicle_caravan)
        val motorhomeLabel = stringResource(R.string.vehicle_motorhome)
        val tentLabel = stringResource(R.string.vehicle_tent)

        spots.forEach { spot ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(spot.name, fontWeight = FontWeight.SemiBold)
                    val typeText = when (spot.type) {
                        "caravan_site" -> caravanSiteLabel
                        "camp_site" -> campSiteLabel
                        else -> spot.type
                    }
                    Text(
                        stringResource(R.string.overnight_distance_type, spot.distanceFromStartKm, typeText),
                        style = MaterialTheme.typography.bodySmall
                    )
                    DetourText(spot.distanceFromRouteKm)
                    val feeText = when (spot.hasFee) {
                        true -> feePaidText
                        false -> feeFreeText
                        null -> feeUnknownText
                    }
                    Text(feeText, style = MaterialTheme.typography.labelSmall)

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VehicleBadge(caravanLabel, spot.allowsCaravan)
                        VehicleBadge(motorhomeLabel, spot.allowsMotorhome)
                        VehicleBadge(tentLabel, spot.allowsTent)
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleBadge(label: String, allowed: Boolean?) {
    val (text, color) = when (allowed) {
        true -> "$label ✓" to MaterialTheme.colorScheme.primary
        false -> "$label ✗" to MaterialTheme.colorScheme.error
        null -> "$label ?" to MaterialTheme.colorScheme.outline
    }
    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color)
    )
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
