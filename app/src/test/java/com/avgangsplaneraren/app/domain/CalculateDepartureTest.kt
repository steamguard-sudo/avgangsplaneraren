package com.avgangsplaneraren.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun `anpassad rasttid per stopp paverkar totalen`() {
        val arrival = LocalDateTime.of(2026, 7, 24, 17, 0)
        val trip = TripInput(
            fromPlace = "Jönköping",
            toPlace = "Umeå",
            desiredArrival = arrival,
            bufferMinutes = 10,
            minutesPerBreak = 45 // längre raster än standardvärdet 20
        )
        // 5 timmar körning -> floor(300/120) = 2 raster
        val route = RouteInfo(distanceKm = 500, driveMinutes = 300.0)

        val result = calculator.calculate(trip, route)

        assertEquals(90, result.restMinutes) // 2 x 45 min
        // total = 300 (körning) + 90 (rast) + 10 (buffert) = 400 min
        assertEquals(arrival.minusMinutes(400), result.departureTime)
    }

    @Test
    fun `campingstopp laggs till i totalen`() {
        val arrival = LocalDateTime.of(2026, 7, 24, 17, 0)
        val trip = TripInput(
            fromPlace = "Jönköping",
            toPlace = "Norrköping",
            desiredArrival = arrival,
            bufferMinutes = 10,
            campingStopMinutes = 90 // t.ex. matlagning vid en övernattningsplats
        )
        // 90 min körning, under 120 min-gränsen -> inga vanliga raster
        val route = RouteInfo(distanceKm = 130, driveMinutes = 90.0)

        val result = calculator.calculate(trip, route)

        // total = 90 (körning) + 0 (rast) + 10 (buffert) + 90 (camping) = 190 min
        assertEquals(arrival.minusMinutes(190), result.departureTime)
    }

    @Test
    fun `negativ rasttid eller campingtid kastar fel`() {
        val arrival = LocalDateTime.of(2026, 7, 24, 17, 0)
        val route = RouteInfo(distanceKm = 100, driveMinutes = 60.0)

        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(
                TripInput("A", "B", arrival, minutesPerBreak = -5),
                route
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(
                TripInput("A", "B", arrival, campingStopMinutes = -5),
                route
            )
        }
    }
}
