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
    val source: String = "OpenStreetMap"
)
