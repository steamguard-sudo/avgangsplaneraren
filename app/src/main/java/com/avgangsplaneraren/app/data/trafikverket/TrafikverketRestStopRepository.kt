package com.avgangsplaneraren.app.data.trafikverket

import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.RestStop
import com.avgangsplaneraren.app.domain.RestStopProvider
import com.avgangsplaneraren.app.domain.haversineKm
import kotlinx.coroutines.runBlocking

/**
 * Verklig implementation av [RestStopProvider], baserad på Trafikverkets
 * NVDB-dataprodukt "Rastplats" (se README och teknisk-plan-avgangsplaneraren.md,
 * avsnitt 3.2).
 *
 * Notera att [RestStopProvider.candidatesNear] i domänlagret är en synkron
 * funktion (för att hålla domänlagret enkelt och testbart utan coroutines).
 * Eftersom sökningen sker mot en lokal, redan nedladdad SQLite-databas
 * (ingen nätverksrensning) är detta ett rimligt val här – `runBlocking`
 * blockerar bara mot lokal disk-I/O, inte nätverk.
 *
 * @param radiusKm hur nära ruttpunkten en rastplats får ligga för att räknas
 *   som en kandidat (standard 3 km).
 */
class TrafikverketRestStopRepository(
    private val dao: RestAreaDao,
    private val radiusKm: Double = 3.0
) : RestStopProvider {

    override fun candidatesNear(point: Coordinates, distanceFromStartKm: Int): List<RestStop> {
        // Grov bounding box i grader. 1° latitud ≈ 111 km; longitud varierar
        // med breddgrad, men en enkel överskattning duger för grovfiltret –
        // exakt avstånd räknas ut nedan med haversine.
        val latDelta = radiusKm / 111.0
        val lonDelta = radiusKm / 60.0

        val boxResults = runBlocking {
            dao.findInBoundingBox(
                minLat = point.lat - latDelta,
                maxLat = point.lat + latDelta,
                minLon = point.lon - lonDelta,
                maxLon = point.lon + lonDelta
            )
        }

        return boxResults
            .map { entity -> entity to haversineKm(point, Coordinates(entity.latitud, entity.longitud)) }
            .filter { (_, distance) -> distance <= radiusKm }
            .sortedBy { (_, distance) -> distance }
            .map { (entity, _) -> entity.toRestStop(distanceFromStartKm) }
    }
}

private fun RestAreaEntity.toRestStop(distanceFromStartKm: Int) = RestStop(
    name = namn ?: "Rastplats vid ${vagnummer ?: "okänd väg"}",
    latitude = latitud,
    longitude = longitud,
    hasTable = harBord,
    hasBench = harBank,
    hasToilet = harToalett,
    distanceFromStartKm = distanceFromStartKm,
    arrivalAtStop = java.time.LocalDateTime.now() // skrivs över i CalculateDeparture
)
