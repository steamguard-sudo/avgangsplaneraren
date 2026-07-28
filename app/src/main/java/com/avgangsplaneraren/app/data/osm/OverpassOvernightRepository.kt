package com.avgangsplaneraren.app.data.osm

import android.util.Log
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
 * Om anropet misslyckas returneras en tom lista — övernattningsförslag är
 * en "nice to have"-funktion, så ett fel här ska aldrig störa resten av
 * appen (till skillnad från t.ex. ruttberäkningen).
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
        return try {
            val typesParam = types.joinToString(",") { it.osmTag }
            api.search(point.lat, point.lon, radiusKm, typesParam).spots.map { dto ->
                OvernightSpot(
                    name = dto.name ?: "Namnlös plats (OpenStreetMap)",
                    latitude = dto.lat,
                    longitude = dto.lon,
                    distanceFromStartKm = distanceFromStartKm,
                    type = dto.type,
                    hasFee = dto.hasFee
                )
            }
        } catch (e: Exception) {
            Log.w("OverpassOvernightRepository", "Kunde inte hämta övernattningsplatser", e)
            emptyList()
        }
    }
}
