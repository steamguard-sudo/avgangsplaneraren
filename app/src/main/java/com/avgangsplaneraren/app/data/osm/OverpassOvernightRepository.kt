package com.avgangsplaneraren.app.data.osm

import com.avgangsplaneraren.app.data.BackendHttp
import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.OvernightSpot
import com.avgangsplaneraren.app.domain.OvernightSpotProvider
import com.avgangsplaneraren.app.domain.OvernightSpotType

class OverpassOvernightRepository(baseUrl: String) : OvernightSpotProvider {

    private val api: BackendOvernightApi =
        BackendHttp.retrofit(baseUrl).create(BackendOvernightApi::class.java)

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
                distanceFromRouteKm = dto.distanceFromRouteKm,
                phoneNumber = dto.phone
            )
        }
    }
}
