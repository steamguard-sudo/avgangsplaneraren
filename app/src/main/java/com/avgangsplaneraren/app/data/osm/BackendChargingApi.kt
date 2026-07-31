package com.avgangsplaneraren.app.data.osm

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface BackendChargingApi {

    @GET("charging")
    suspend fun search(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radiusKm") radiusKm: Double
    ): ChargingResponse
}

@Serializable
data class ChargingResponse(
    val stations: List<ChargingStationDto>
)

@Serializable
data class ChargingStationDto(
    val id: String,
    val name: String? = null,
    val lat: Double,
    val lon: Double,
    val operator: String? = null,
    val capacity: Int? = null,
    val hasFee: Boolean? = null,
    val phone: String? = null,
    val distanceFromRouteKm: Double = 0.0
)
