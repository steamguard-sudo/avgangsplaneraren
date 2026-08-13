package com.avgangsplaneraren.app.data.nobil

import com.avgangsplaneraren.app.data.osm.ChargingResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit-gränssnitt mot backendens /charging-nobil-endpoint (NOBIL —
 * Norges/Sveriges officiella laddstationsregister). Återanvänder
 * ChargingResponse/ChargingStationDto från data.osm-paketet oförändrat,
 * eftersom backenden svarar med exakt samma fältnamn som /charging
 * (OpenStreetMap/Overpass-varianten).
 */
interface BackendNobilChargingApi {

    @GET("charging-nobil")
    suspend fun search(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radiusKm") radiusKm: Double
    ): ChargingResponse
}
