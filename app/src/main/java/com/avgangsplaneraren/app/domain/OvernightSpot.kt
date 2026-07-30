package com.avgangsplaneraren.app.domain

/**
 * En övernattningsmöjlighet längs rutten (husbil/husvagn/tältplats),
 * hämtad från OpenStreetMap — inte samma sak som en rastplats.
 * Rastplatser (Trafikverket) är till för korta stopp under körning;
 * det här är till för resenärer som planerar att övernatta någonstans
 * på vägen mot slutmålet.
 */
data class OvernightSpot(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceFromStartKm: Int,
    /** OSM-taggen som platsen hittades via, t.ex. "caravan_site" eller "camp_site". */
    val type: String,
    /** null = okänt (inte angivet i OSM), annars om platsen kostar pengar. */
    val hasFee: Boolean?,
    /**
     * Vilka ekipage som är välkomna, enligt OSM-taggarna `caravans`,
     * `motorhome` och `tents`. null = inte angivet i OSM — vanligt, eftersom
     * OSM-communityn själva flaggar att den här detaljnivån ofta saknas.
     * Visa alltså "okänt", inte "nej", när värdet är null.
     */
    val allowsCaravan: Boolean?,
    val allowsMotorhome: Boolean?,
    val allowsTent: Boolean?,
    /** Fågelvägen (km) från den sökta ruttpunkten till platsen — INTE körsträcka. */
    val distanceFromRouteKm: Double,
    val source: String = "OpenStreetMap"
)
