package com.avgangsplaneraren.app.domain

/**
 * De typer av övernattningsplatser som kan sökas efter, motsvarande
 * OpenStreetMaps `tourism`-taggar.
 */
enum class OvernightSpotType(val osmTag: String) {
    /** Husbils-/husvagnsplats — `tourism=caravan_site`. */
    CARAVAN_SITE("caravan_site"),
    /** Campingplats (kan inkludera tält) — `tourism=camp_site`. */
    CAMP_SITE("camp_site")
}

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
     * @param types vilka platstyper som ska sökas efter — bägge som standard.
     */
    suspend fun candidatesNear(
        point: Coordinates,
        distanceFromStartKm: Int,
        radiusKm: Double = 40.0,
        types: Set<OvernightSpotType> = setOf(OvernightSpotType.CARAVAN_SITE, OvernightSpotType.CAMP_SITE)
    ): List<OvernightSpot>
}
