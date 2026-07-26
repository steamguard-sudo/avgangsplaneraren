package com.avgangsplaneraren.app.data.directions

import com.avgangsplaneraren.app.domain.Coordinates

/**
 * Avkodar Googles "encoded polyline"-format (samma format som används av
 * både Directions API och Routes API) till en lista av [Coordinates].
 *
 * Algoritmen är Googles egen, väldokumenterad standard:
 * https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 */
object PolylineDecoder {

    fun decode(encoded: String): List<Coordinates> {
        val points = mutableListOf<Coordinates>()
        var index = 0
        var lat = 0
        var lon = 0

        while (index < encoded.length) {
            lat += decodeSignedValue(encoded, index).also { index = it.second }.first
            lon += decodeSignedValue(encoded, index).also { index = it.second }.first
            points += Coordinates(lat / 1e5, lon / 1e5)
        }
        return points
    }

    /** Avkodar ett enda signerat, varbyte-kodat värde. Returnerar (värde, nytt index). */
    private fun decodeSignedValue(encoded: String, startIndex: Int): Pair<Int, Int> {
        var index = startIndex
        var result = 0
        var shift = 0
        var byte: Int
        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        val delta = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
        return delta to index
    }
}
