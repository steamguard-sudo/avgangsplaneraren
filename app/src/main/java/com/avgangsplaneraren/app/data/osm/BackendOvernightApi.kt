package com.avgangsplaneraren.app.data.osm

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface BackendOvernightApi {

    @GET("overnight")
    suspend fun search(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radiusKm") radiusKm: Double,
        @Query("types") types: String
    ): OvernightResponse
}

@Serializable
data class OvernightResponse(
    val spots: List<OvernightSpotDto>
)

@Serializable
data class OvernightSpotDto(
    val id: String,
    val name: String? = null,
    val lat: Double,
    val lon: Double,
    val type: String,
    val hasFee: Boolean? = null,
    val allowsCaravan: Boolean? = null,
    val allowsMotorhome: Boolean? = null,
    val allowsTent: Boolean? = null,
    val distanceFromRouteKm: Double = 0.0,
    val phone: String? = null
)
