package com.avgangsplaneraren.app.domain

import java.time.LocalDateTime

/**
 * Indata från användaren i planeringsformuläret.
 */
data class TripInput(
    val fromPlace: String,
    val toPlace: String,
    val desiredArrival: LocalDateTime,
    val bufferMinutes: Int = 10,
    val onlyStopsWithTableAndBench: Boolean = false
)

/**
 * Resultat av en ruttförfrågan (från t.ex. data/directions).
 * distanceKm och driveMinutes hämtas i skarp version från Directions API,
 * men domänlagret bryr sig bara om värdena – inte varifrån de kommer.
 *
 * @param polyline en approximativ linje för rutten (start → mål, ev. med
 *   mellanpunkter). Används för att placera ut rastplatsförslag på rätt
 *   plats. Kan vara tom om ingen ruttgeometri finns tillgänglig, då faller
 *   rastplatssökningen tillbaka på enbart avstånd.
 */
data class RouteInfo(
    val distanceKm: Int,
    val driveMinutes: Double,
    val polyline: List<Coordinates> = emptyList()
)

data class RestStop(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val hasTable: Boolean,
    val hasBench: Boolean,
    val hasToilet: Boolean,
    val distanceFromStartKm: Int,
    val arrivalAtStop: LocalDateTime
)

data class DepartureResult(
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val distanceKm: Int,
    val driveMinutes: Double,
    val restMinutes: Int,
    val restStops: List<RestStop>
)
