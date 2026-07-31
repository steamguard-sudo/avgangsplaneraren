package com.avgangsplaneraren.app.domain

data class OvernightSpot(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceFromStartKm: Int,
    val type: String,
    val hasFee: Boolean?,
    val allowsCaravan: Boolean?,
    val allowsMotorhome: Boolean?,
    val allowsTent: Boolean?,
    val distanceFromRouteKm: Double,
    val phoneNumber: String? = null,
    val source: String = "OpenStreetMap"
)
