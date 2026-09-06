package com.avgangsplaneraren.app.domain

import java.time.LocalDateTime

/**
 * Indata från användaren i planeringsformuläret.
 *
 * @param minutesPerBreak hur lång varje rast ungefär tar, i minuter.
 *   Användarstyrt istället för hårdkodat, eftersom folk rastar olika länge.
 * @param campingStopMinutes extra tid för ett planerat camping-/övernattnings-
 *   stopp under resan (utöver de vanliga korta rasterna), t.ex. om man tänker
 *   laga mat eller ta en längre paus vid en övernattningsplats. 0 om inget
 *   sådant stopp planeras.
 */
data class TripInput(
    val fromPlace: String,
    val toPlace: String,
    val desiredArrival: LocalDateTime,
    val bufferMinutes: Int = 10,
    val onlyStopsWithTableAndBench: Boolean = false,
    val minutesPerBreak: Int = 20,
    val campingStopMinutes: Int = 0
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
 * @param isEstimated true om detta INTE är en riktig vägrutt utan
 *   `RouteEstimator`:s fallback: en rak linje mellan start och mål med
 *   schablonhastigheter, som används när det riktiga ruttanropet misslyckas.
 *   Då är distanceKm, driveMinutes och polyline alla grova uppskattningar,
 *   och rastplats-/ladd-/övernattningspunkter som placeras längs linjen kan
 *   hamna en bit från den verkliga vägen. Propageras vidare till
 *   [DepartureResult.isEstimatedRoute] så att UI:t kan varna.
 */
data class RouteInfo(
    val distanceKm: Int,
    val driveMinutes: Double,
    val polyline: List<Coordinates> = emptyList(),
    val isEstimated: Boolean = false
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

/**
 * @param plannedBreaks antal raster som lades in utifrån körtiden. Skiljer
 *   "kort resa – inga raster planerades" (0) från "raster planerades men
 *   ingen rastplats kunde hittas nära rutten" (> 0 medan [restStops] är tom),
 *   så att UI:t kan visa rätt sak i stället för att bara dölja sektionen.
 * @param isEstimatedRoute true om [RouteInfo.isEstimated] var satt för rutten
 *   som resultatet bygger på — dvs. den riktiga rutten kunde inte hämtas och
 *   en rak linje användes. UI:t visar då en tydlig varning eftersom utplacerade
 *   rast-, ladd- och övernattningspunkter kan ligga vid sidan av den verkliga
 *   vägen och sträcka/körtid bara är ungefärliga.
 */
data class DepartureResult(
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val distanceKm: Int,
    val driveMinutes: Double,
    val restMinutes: Int,
    val restStops: List<RestStop>,
    val plannedBreaks: Int = 0,
    val isEstimatedRoute: Boolean = false
)
