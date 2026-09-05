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
 * Trafikverket har bara ca 270 officiella rastplatser i hela landet (se
 * trafikverket.se/resa-och-trafik/vag/Rastplatser), så avståndet mellan dem
 * kan lätt bli 10-25 km i glesare trakter. En fast, snäv radie (tidigare
 * 7 km) missar därför ofta helt i sådana områden även om en rastplats
 * finns på fullt rimligt avstånd. Sökningen vidgas därför stegvis: 7 km
 * (nära/exakt), sedan 15 km, sedan 30 km, innan vi ger upp helt.
 *
 * @param radiusStepsKm radier att försöka i tur och ordning, från snävast
 *   till vidast.
 */
class TrafikverketRestStopRepository(
    private val dao: RestAreaDao,
    private val radiusStepsKm: List<Double> = listOf(7.0, 15.0, 30.0)
) : RestStopProvider {

    override fun candidatesNear(point: Coordinates, distanceFromStartKm: Int): List<RestStop> {
        for (radiusKm in radiusStepsKm) {
            val matches = findWithinRadius(point, radiusKm, distanceFromStartKm)
            if (matches.isNotEmpty()) return matches
        }
        return emptyList()
    }

    private fun findWithinRadius(
        point: Coordinates,
        radiusKm: Double,
        distanceFromStartKm: Int
    ): List<RestStop> {
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
