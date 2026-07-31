package com.avgangsplaneraren.app.domain

data class ChargingStation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceFromStartKm: Int,
    val operator: String?,
    val capacity: Int?,
    val hasFee: Boolean?,
    val phoneNumber: String?,
    val distanceFromRouteKm: Double,
    val source: String = "OpenStreetMap"
)
