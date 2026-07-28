package com.avgangsplaneraren.app.domain

import java.time.LocalDateTime
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Kärnlogiken för appen: räknar ut när användaren måste avgå,
 * och var på vägen rastplatser bör föreslås.
 *
 * Denna klass är medvetet fri från Android-specifika typer (ingen Context,
 * ingen Retrofit/Room-modell direkt) så att den går att portera rakt av
 * till Swift när iOS-versionen byggs.
 *
 * @param breakIntervalMinutes hur ofta (i körtid) en rast läggs in, t.ex. var 2:e timme.
 * @param restStopProvider källa för rastplatsförslag – i skarp version en
 *   `TrafikverketRestStopRepository` (se data/trafikverket) som slår upp
 *   riktiga rastplatser från NVDB nära en given koordinat.
 */
class CalculateDeparture(
    private val breakIntervalMinutes: Int = 120,
    private val restStopProvider: RestStopProvider = RestStopProvider.placeholder()
) {

    /**
     * Beräknar avgångstid och rastplatser för en given resa.
     *
     * @param trip Användarens indata (avresa, mål, önskad ankomsttid, buffert,
     *   rasttid per stopp, ev. campingstopp).
     * @param route Ruttdata (sträcka, körtid och ev. ruttlinje), hämtat från
     *   t.ex. Google Directions API.
     */
    fun calculate(trip: TripInput, route: RouteInfo): DepartureResult {
        require(route.driveMinutes >= 0) { "Körtid kan inte vara negativ" }
        require(route.distanceKm >= 0) { "Sträcka kan inte vara negativ" }
        require(trip.minutesPerBreak >= 0) { "Rasttid kan inte vara negativ" }
        require(trip.campingStopMinutes >= 0) { "Campingstopp kan inte vara negativ" }

        val numBreaks = floor(route.driveMinutes / breakIntervalMinutes).toInt().coerceAtLeast(0)
        val restMinutes = numBreaks * trip.minutesPerBreak

        val totalMinutes = route.driveMinutes + restMinutes + trip.bufferMinutes + trip.campingStopMinutes
        val departureTime = trip.desiredArrival.minusMinutes(totalMinutes.roundToInt().toLong())

        val restStops = buildRestStops(
            numBreaks = numBreaks,
            route = route,
            departureTime = departureTime,
            onlyWithTableAndBench = trip.onlyStopsWithTableAndBench,
            minutesPerBreak = trip.minutesPerBreak
        )

        return DepartureResult(
            departureTime = departureTime,
            arrivalTime = trip.desiredArrival,
            distanceKm = route.distanceKm,
            driveMinutes = route.driveMinutes,
            restMinutes = restMinutes,
            restStops = restStops
        )
    }

    private fun buildRestStops(
        numBreaks: Int,
        route: RouteInfo,
        departureTime: LocalDateTime,
        onlyWithTableAndBench: Boolean,
        minutesPerBreak: Int
    ): List<RestStop> {
        if (numBreaks == 0) return emptyList()

        var runningTime = departureTime
        val stops = mutableListOf<RestStop>()

        for (i in 1..numBreaks) {
            runningTime = runningTime.plusMinutes(breakIntervalMinutes.toLong())
            val fraction = i.toDouble() / (numBreaks + 1)
            val pointOnRoute = pointAtFraction(route, fraction)
            val distanceAtStop = (route.distanceKm * fraction).roundToInt()

            val candidates = restStopProvider.candidatesNear(
                point = pointOnRoute,
                distanceFromStartKm = distanceAtStop
            )
            val chosen = RestStopFilter.apply(candidates, onlyWithTableAndBench).firstOrNull()

            if (chosen != null) {
                stops += chosen.copy(arrivalAtStop = runningTime)
            }
            runningTime = runningTime.plusMinutes(minutesPerBreak.toLong())
        }
        return stops
    }

    /** Interpolerar en punkt längs ruttens polyline vid given andel (0.0–1.0) av sträckan. */
    private fun pointAtFraction(route: RouteInfo, fraction: Double): Coordinates {
        val line = route.polyline
        if (line.size < 2) {
            // Ingen ruttgeometri tillgänglig – kan inte interpolera, returnera första
            // punkten om den finns, annars låt providern hantera fallback själv.
            return line.firstOrNull() ?: Coordinates(0.0, 0.0)
        }
        val index = (fraction * (line.size - 1)).roundToInt().coerceIn(0, line.size - 1)
        return line[index]
    }
}

/**
 * Källa för rastplatser nära en given punkt på rutten. I skarp version
 * implementeras detta av `TrafikverketRestStopRepository` (se
 * data/trafikverket), som söker i en lokalt bundlad databas byggd från
 * Trafikverkets NVDB-data (dataprodukten "Rastplats").
 */
interface RestStopProvider {

    /**
     * @param point ungefärlig position på rutten där en rast bör läggas in.
     * @param distanceFromStartKm hur långt in i resan denna punkt ligger,
     *   för visning i UI:t (t.ex. "ca 240 km från start").
     * @return kandidater nära punkten, ej filtrerade – filtrering på
     *   bord/bänk sker separat via [RestStopFilter].
     */
    fun candidatesNear(point: Coordinates, distanceFromStartKm: Int): List<RestStop>

    companion object {
        /** Enkel platshållar-implementation tills Trafikverket-integrationen är på plats. */
        fun placeholder(): RestStopProvider = PlaceholderRestStopProvider
    }
}

private object PlaceholderRestStopProvider : RestStopProvider {
    private val pool = listOf(
        RestStopTemplate("Rastplats Sjöglimten", hasTable = true, hasBench = true, hasToilet = true),
        RestStopTemplate("Rastplats Björkhaga", hasTable = true, hasBench = true, hasToilet = false),
        RestStopTemplate("Rastplats Granliden", hasTable = false, hasBench = true, hasToilet = false),
        RestStopTemplate("Rastplats Åkanten", hasTable = true, hasBench = true, hasToilet = true),
        RestStopTemplate("Rastplats Kullen", hasTable = false, hasBench = false, hasToilet = false),
        RestStopTemplate("Rastplats Furulund", hasTable = true, hasBench = true, hasToilet = false)
    )
    private var cursor = 0

    override fun candidatesNear(point: Coordinates, distanceFromStartKm: Int): List<RestStop> {
        val template = pool[cursor % pool.size]
        cursor++
        return listOf(
            RestStop(
                name = template.name,
                latitude = point.lat,
                longitude = point.lon,
                hasTable = template.hasTable,
                hasBench = template.hasBench,
                hasToilet = template.hasToilet,
                distanceFromStartKm = distanceFromStartKm,
                arrivalAtStop = LocalDateTime.now() // ersätts i CalculateDeparture
            )
        )
    }

    private data class RestStopTemplate(
        val name: String,
        val hasTable: Boolean,
        val hasBench: Boolean,
        val hasToilet: Boolean
    )
}
