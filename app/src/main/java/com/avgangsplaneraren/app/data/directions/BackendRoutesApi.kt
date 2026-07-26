package com.avgangsplaneraren.app.data.directions

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Anropar ERT EGET backend (se backend/server.js), inte Google direkt.
 * Backend håller Google-API-nyckeln hemlig och cachar ruttsvar mellan
 * användare — se resonemanget i teknisk-plan-avgangsplaneraren.md,
 * avsnitt 3.1 och 3.3, samt kostnadsdiskussionen i chatten om varför
 * cachning på serversidan är den enskilt största kostnadsbesparingen.
 */
interface BackendRoutesApi {

    @GET("route")
    suspend fun getRoute(
        @Query("fromLat") fromLat: Double,
        @Query("fromLon") fromLon: Double,
        @Query("toLat") toLat: Double,
        @Query("toLon") toLon: Double
    ): BackendRouteResponse
}

/** Matchar JSON-svaret från backend/server.js (se `toBackendResponse` där). */
@Serializable
data class BackendRouteResponse(
    val distanceKm: Int,
    val driveMinutes: Double,
    val encodedPolyline: String,
    val cached: Boolean = false
)
