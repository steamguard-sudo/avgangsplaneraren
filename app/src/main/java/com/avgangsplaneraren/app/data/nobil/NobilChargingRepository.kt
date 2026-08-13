package com.avgangsplaneraren.app.data.nobil

import com.avgangsplaneraren.app.domain.ChargingStation
import com.avgangsplaneraren.app.domain.ChargingStationProvider
import com.avgangsplaneraren.app.domain.Coordinates
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

/**
 * ChargingStationProvider baserad på NOBIL (Norges/Sveriges officiella
 * laddstationsregister, drivs av Enova/Energimyndigheten) via backendens
 * /charging-nobil-endpoint — ett alternativ till [com.avgangsplaneraren.app.data.osm.OverpassChargingRepository],
 * som frågar OpenStreetMap istället. Samma domänmodell (ChargingStation)
 * och samma gränssnitt, så de går att byta mellan fritt.
 */
class NobilChargingRepository(baseUrl: String) : ChargingStationProvider {

    private val api: BackendNobilChargingApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .asConverterFactory("application/json".toMediaType())
        )
        .build()
        .create(BackendNobilChargingApi::class.java)

    override suspend fun candidatesNear(
        point: Coordinates,
        distanceFromStartKm: Int,
        radiusKm: Double
    ): List<ChargingStation> {
        return api.search(point.lat, point.lon, radiusKm).stations.map { dto ->
            ChargingStation(
                name = dto.name ?: "Namnlös laddplats (NOBIL)",
                latitude = dto.lat,
                longitude = dto.lon,
                distanceFromStartKm = distanceFromStartKm,
                operator = dto.operator,
                capacity = dto.capacity,
                hasFee = dto.hasFee,
                phoneNumber = dto.phone,
                distanceFromRouteKm = dto.distanceFromRouteKm,
                source = "NOBIL"
            )
        }
    }
}
