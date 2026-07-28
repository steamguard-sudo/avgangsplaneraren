package com.avgangsplaneraren.app.domain

/**
 * Källa för övernattningsplatser nära en given punkt. I skarp version
 * implementeras detta av `OverpassOvernightRepository` (se data/osm),
 * som anropar OpenStreetMaps Overpass API via backend.
 */
interface OvernightSpotProvider {
    /**
     * @param point punkten att söka runt (t.ex. en punkt på ruttlinjen).
     * @param distanceFromStartKm hur långt in i resan punkten ligger, för visning i UI:t.
     * @param radiusKm hur brett sökområde runt punkten som ska genomsökas.
     */
    suspend fun candidatesNear(
        point: Coordinates,
        distanceFromStartKm: Int,
        radiusKm: Double = 20.0
    ): List<OvernightSpot>
}
