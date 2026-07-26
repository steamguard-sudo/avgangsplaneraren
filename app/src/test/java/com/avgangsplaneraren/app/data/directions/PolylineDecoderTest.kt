package com.avgangsplaneraren.app.data.directions

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun `avkodar Googles dokumenterade exempel korrekt`() {
        // Facit hämtat direkt från Googles egen algoritmdokumentation:
        // https://developers.google.com/maps/documentation/utilities/polylinealgorithm
        val result = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, result.size)
        assertEquals(38.5, result[0].lat, 0.00001)
        assertEquals(-120.2, result[0].lon, 0.00001)
        assertEquals(40.7, result[1].lat, 0.00001)
        assertEquals(-120.95, result[1].lon, 0.00001)
        assertEquals(43.252, result[2].lat, 0.00001)
        assertEquals(-126.453, result[2].lon, 0.00001)
    }
}
