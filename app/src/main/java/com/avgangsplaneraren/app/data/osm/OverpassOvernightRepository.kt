package com.avgangsplaneraren.app.data.osm

import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.OvernightSpot
import com.avgangsplaneraren.app.domain.OvernightSpotProvider
import com.avgangsplaneraren.app.domain.OvernightSpotType
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

/**
 * Riktig implementation av [OvernightSpotProvider], via backend/server.js
 * (som i sin tur anropar OpenStreetMaps Overpass API och cachar svaret).
 *
 * Kastar vidare eventuella fel istället för att tysta ner dem — anroparen
 * (se `findOvernightSpotsAlongRoute` i PlannerScreen) behöver kunna skilja
 * på "sökningen lyckades men inget hittades" och "sökningen misslyckades
 * (t.ex. Overpass överbelastad)", för att kunna visa rätt meddelande.
 */
class OverpassOvernightRepository(baseUrl: String) : OvernightSpotProvider {

    private val api: BackendOvernightApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .asConverterFactory("application/json".toMediaType())
        )
        .build()
        .create(BackendOvernightApi::class.java)

    override suspend fun candidatesNear(
        point: Coordinates,
        distanceFromStartKm: Int,
        radiusKm: Double,
        types: Set<OvernightSpotType>
    ): List<OvernightSpot> {
        if (types.isEmpty()) return emptyList()
        val typesParam = types.joinToString(",") { it.osmTag }
        return api.search(point.lat, point.lon, radiusKm, typesParam).spots.map { dto ->
            OvernightSpot(
                name = dto.name ?: "Namnlös plats (OpenStreetMap)",
                latitude = dto.lat,
                longitude = dto.lon,
                distanceFromStartKm = distanceFromStartKm,
                type = dto.type,
                hasFee = dto.hasFee,
                allowsCaravan = dto.allowsCaravan,
                allowsMotorhome = dto.allowsMotorhome,
                allowsTent = dto.allowsTent,
                distanceFromRouteKm = dto.distanceFromRouteKm
            )
        }
    }
}
