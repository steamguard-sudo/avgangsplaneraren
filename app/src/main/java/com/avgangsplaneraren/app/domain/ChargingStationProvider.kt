package com.avgangsplaneraren.app.domain

interface ChargingStationProvider {
    suspend fun candidatesNear(
        point: Coordinates,
        distanceFromStartKm: Int,
        radiusKm: Double = 40.0
    ): List<ChargingStation>
}
