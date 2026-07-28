package com.avgangsplaneraren.app.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avgangsplaneraren.app.domain.OvernightSpot

/**
 * Visar övernattningsförslag längs rutten (husbil/husvagn/tältplats),
 * tydligt separerat från rastplatserna och märkt med källa — dessa kommer
 * från OpenStreetMap, inte Trafikverket, och kvalitet/täckning kan variera
 * mer eftersom det är community-underhållen data.
 */
@Composable
fun OvernightSpotsSection(spots: List<OvernightSpot>) {
    if (spots.isEmpty()) return

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("Övernattningsmöjligheter i närheten", style = MaterialTheme.typography.titleMedium)
        Text(
            "Källa: OpenStreetMap-communityn — dubbelkolla alltid på plats.",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        spots.forEach { spot ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(spot.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Ca ${spot.distanceFromStartKm} km in på resan · ${typeLabel(spot.type)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val feeText = when (spot.hasFee) {
                        true -> "Kostar pengar"
                        false -> "Gratis"
                        null -> "Pris okänt"
                    }
                    Text(feeText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    "caravan_site" -> "Husbils-/husvagnsplats"
    "camp_site" -> "Campingplats"
    else -> type
}
