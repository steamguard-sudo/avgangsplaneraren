package com.avgangsplaneraren.app.domain

/**
 * Källa för fritextsökning av platser/adresser, t.ex. Google Places API
 * (via backend, se [com.avgangsplaneraren.app.data.directions.GooglePlacesRepository]).
 */
interface PlaceProvider {
    /** Returnerar förslag medan användaren skriver. Tom lista om inget matchar. */
    suspend fun autocomplete(query: String): List<PlaceSuggestion>

    /** Slår upp koordinaten för en tidigare vald [PlaceSuggestion.placeId]. */
    suspend fun details(placeId: String): Coordinates
}
