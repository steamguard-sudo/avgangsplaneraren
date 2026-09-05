package com.avgangsplaneraren.app.data.osm

import com.avgangsplaneraren.app.data.BackendHttp
import com.avgangsplaneraren.app.domain.ChargingStation
import com.avgangsplaneraren.app.domain.ChargingStationProvider
import com.avgangsplaneraren.app.domain.Coordinates

class OverpassChargingRepository(baseUrl: String) : ChargingStationProvider {

    private val api: BackendChargingApi =
        BackendHttp.retrofit(baseUrl).create(BackendChargingApi::class.java)

    override suspend fun candidatesNear(
        point: Coordinates,
        distanceFromStartKm: Int,
        radiusKm: Double
    ): List<ChargingStation> {
        return api.search(point.lat, point.lon, radiusKm).stations.map { dto ->
            ChargingStation(
                name = dto.name ?: "Namnlös laddplats (OpenStreetMap)",
                latitude = dto.lat,
                longitude = dto.lon,
                distanceFromStartKm = distanceFromStartKm,
                operator = dto.operator,
                capacity = dto.capacity,
                hasFee = dto.hasFee,
                distanceFromRouteKm = dto.distanceFromRouteKm
            )
        }
    }
}
