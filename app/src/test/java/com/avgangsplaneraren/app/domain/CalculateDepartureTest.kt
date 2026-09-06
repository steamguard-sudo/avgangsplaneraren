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

    @Test
    fun `brytpunkt placeras efter faktisk baglangd, inte efter punktindex`() {
        // Medvetet ojämn polyline: p0->p1 är bara ~1,1 km, p1->p2 ~221 km.
        // Den gamla metoden (indexera line[fraction * lastIndex]) skulle vid
        // fraction 0.5 välja line[1] = p1, dvs ~1 km in på en 222 km-resa.
        // Kumulativ båglängd ska i stället landa ungefär mitt på det långa
        // segmentet (runt lat 59.0).
        val p0 = Coordinates(58.0000, 15.0000)
        val p1 = Coordinates(58.0100, 15.0000) // ~1,1 km norr om p0
        val p2 = Coordinates(60.0000, 15.0000) // ~221 km norr om p1
        val route = RouteInfo(
            distanceKm = 222,
            driveMinutes = 180.0, // floor(180/120) = 1 rast -> fraction = 0.5
            polyline = listOf(p0, p1, p2)
        )

        val provider = CapturingRestStopProvider()
        val calc = CalculateDeparture(restStopProvider = provider)

        calc.calculate(
            TripInput("A", "B", LocalDateTime.of(2026, 7, 24, 17, 0)),
            route
        )

        assertEquals(1, provider.searchedPoints.size)
        val searched = provider.searchedPoints.single()

        // Gamla beteendet gav exakt p1 (0 km bort). Nya ska ligga ~110 km från
        // p1, ungefär halvvägs längs den långa raksträckan.
        val kmFromP1 = haversineKm(p1, searched)
        assertTrue(
            "brytpunkten hamnade ${kmFromP1.toInt()} km från p1 – förväntade ~110 km",
            kmFromP1 > 90.0
        )
        // Fortfarande på linjen (samma longitud).
        assertEquals(15.0, searched.lon, 0.0001)
    }

    /** Fångar vilken koordinat rastplatssökningen faktiskt gjordes på. */
    private class CapturingRestStopProvider : RestStopProvider {
        val searchedPoints = mutableListOf<Coordinates>()

        override fun candidatesNear(point: Coordinates, distanceFromStartKm: Int): List<RestStop> {
            searchedPoints += point
            return listOf(
                RestStop(
                    name = "Testrastplats",
                    latitude = point.lat,
                    longitude = point.lon,
                    hasTable = true,
                    hasBench = true,
                    hasToilet = true,
                    distanceFromStartKm = distanceFromStartKm,
                    arrivalAtStop = LocalDateTime.of(2026, 7, 24, 12, 0)
                )
            )
        }
    }
}
