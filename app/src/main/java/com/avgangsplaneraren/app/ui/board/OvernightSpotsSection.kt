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
 *
 * @param searchDone true om en sökning faktiskt kördes (oavsett resultat) —
 *   används för att skilja på "ingen sökning gjord än" (visa ingenting) och
 *   "sökning gjord men inget hittades" (visa ett tydligt meddelande, så det
 *   inte ser ut som ett fel).
 * @param searchFailed true om minst ett sökanrop misslyckades (t.ex. Overpass
 *   överbelastad) — skiljer "servern svarade inte" från "inga platser finns
 *   i området", som annars skulle se identiska ut för användaren.
 */
@Composable
fun OvernightSpotsSection(spots: List<OvernightSpot>, searchDone: Boolean, searchFailed: Boolean = false) {
    if (!searchDone) return

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("Övernattningsmöjligheter i närheten", style = MaterialTheme.typography.titleMedium)
        Text(
            "Källa: OpenStreetMap-communityn — dubbelkolla alltid på plats.",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (searchFailed) {
            Text(
                if (spots.isEmpty()) {
                    "Kunde inte söka just nu — OpenStreetMaps sökserver verkar vara " +
                        "överbelastad. Försök igen om en liten stund, det brukar lösa sig."
                } else {
                    "Sökningen lyckades bara delvis (servern var överbelastad på vissa " +
                        "delsträckor) — listan nedan kan vara ofullständig."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else if (spots.isEmpty()) {
            Text(
                "Inga övernattningsplatser hittades längs den här rutten. " +
                    "Det betyder oftast att OpenStreetMap saknar kartlagda platser i " +
                    "området, inte att det inte finns några i verkligheten.",
                style = MaterialTheme.typography.bodySmall
            )
            return
        }

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
