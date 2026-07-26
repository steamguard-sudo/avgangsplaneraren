package com.avgangsplaneraren.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CalculateDepartureTest {

    private val calculator = CalculateDeparture()

    @Test
    fun `kort resa utan rast ger avgang = ankomst minus korrid minus buffert`() {
        val arrival = LocalDateTime.of(2026, 7, 24, 17, 0)
        val trip = TripInput(
            fromPlace = "Jönköping",
            toPlace = "Norrköping",
            desiredArrival = arrival,
            bufferMinutes = 10
        )
        // 90 min körning, under 120 min-gränsen -> inga raster
        val route = RouteInfo(distanceKm = 130, driveMinutes = 90.0)

        val result = calculator.calculate(trip, route)

        assertEquals(0, result.restMinutes)
        assertEquals(arrival.minusMinutes(100), result.departureTime) // 90 + 10 buffert
        assertTrue(result.restStops.isEmpty())
    }

    @Test
    fun `lang resa lagger in raster var 2 timme`() {
        val arrival = LocalDateTime.of(2026, 7, 24, 17, 0)
        val trip = TripInput(
            fromPlace = "Jönköping",
            toPlace = "Umeå",
            desiredArrival = arrival,
            bufferMinutes = 10
        )
        // 5 timmar körning -> floor(300/120) = 2 raster
        val route = RouteInfo(distanceKm = 500, driveMinutes = 300.0)

        val result = calculator.calculate(trip, route)

        assertEquals(2, result.restStops.size)
        assertEquals(40, result.restMinutes) // 2 x 20 min
        // total = 300 (körning) + 40 (rast) + 10 (buffert) = 350 min
        assertEquals(arrival.minusMinutes(350), result.departureTime)
    }

    @Test
    fun `filter pa bord och bank faller tillbaka om inget matchar`() {
        val arrival = LocalDateTime.of(2026, 7, 24, 12, 0)
        val trip = TripInput(
            fromPlace = "A",
            toPlace = "B",
            desiredArrival = arrival,
            onlyStopsWithTableAndBench = true
        )
        val route = RouteInfo(distanceKm = 300, driveMinutes = 240.0)

        val result = calculator.calculate(trip, route)

        // Ska aldrig returnera en tom lista, även om filtret är strängt
        assertTrue(result.restStops.isNotEmpty())
    }
}
