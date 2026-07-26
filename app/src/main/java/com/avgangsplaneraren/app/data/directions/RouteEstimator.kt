package com.avgangsplaneraren.app.data.directions

import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.RouteInfo
import com.avgangsplaneraren.app.domain.RouteProvider
import com.avgangsplaneraren.app.domain.haversineKm
import kotlin.math.roundToInt

/**
 * FALLBACK-implementation av [RouteProvider]: ingen nätverksanrop, bara en
 * grov uppskattning baserat på raka avståndet mellan två koordinater, med
 * en enkel rak polyline. Används om det riktiga backend-anropet
 * ([GoogleRoutesRepository]) misslyckas (ingen nätanslutning, backend nere
 * osv.) så att appen fortfarande ger ett rimligt svar istället för att
 * krascha eller visa ett tomt resultat.
 *
 * Google Routes API returnerar en riktig polyline (encoded polyline),
 * vilken avkodas till [Coordinates] av [PolylineDecoder].
 */
class RouteEstimator : RouteProvider {

    override suspend fun getRoute(from: Coordinates, to: Coordinates): RouteInfo {
        val straightLineKm = haversineKm(from, to)
        val distanceKm = (straightLineKm * 1.25).roundToInt() // vägfaktor
        val avgSpeedKmh = when {
            distanceKm > 300 -> 95.0
            distanceKm > 80 -> 85.0
            else -> 65.0
        }
        val driveMinutes = (distanceKm / avgSpeedKmh) * 60.0

        return RouteInfo(
            distanceKm = distanceKm,
            driveMinutes = driveMinutes,
            polyline = interpolatedLine(from, to, steps = 12)
        )
    }

    /** Rak, linjärt interpolerad linje mellan två punkter – ersätts av riktig ruttgeometri. */
    private fun interpolatedLine(from: Coordinates, to: Coordinates, steps: Int): List<Coordinates> {
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            Coordinates(
                lat = from.lat + (to.lat - from.lat) * t,
                lon = from.lon + (to.lon - from.lon) * t
            )
        }
    }
}

/** Ett litet urval svenska städer, samma som i webbprototypen — för demo. */
object SampleCities {
    val all: Map<String, Coordinates> = mapOf(
        "Stockholm" to Coordinates(59.3293, 18.0686),
        "Göteborg" to Coordinates(57.7089, 11.9746),
        "Malmö" to Coordinates(55.6050, 13.0038),
        "Jönköping" to Coordinates(57.7826, 14.1618),
        "Umeå" to Coordinates(63.8258, 20.2630),
        "Karlstad" to Coordinates(59.3793, 13.5036),
        "Sundsvall" to Coordinates(62.3908, 17.3069),
        "Kalmar" to Coordinates(56.6634, 16.3566)
    )
}
