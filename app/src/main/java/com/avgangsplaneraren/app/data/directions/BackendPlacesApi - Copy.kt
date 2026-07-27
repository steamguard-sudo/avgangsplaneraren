package com.avgangsplaneraren.app.data.directions

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Anropar ERT EGET backend (se backend/server.js), inte Google direkt — samma princip som ruttdelen. */
interface BackendPlacesApi {

    @GET("places/autocomplete")
    suspend fun autocomplete(@Query("query") query: String): AutocompleteResponse

    @GET("places/details")
    suspend fun details(@Query("placeId") placeId: String): PlaceDetailsResponse
}

@Serializable
data class AutocompleteResponse(
    val suggestions: List<AutocompleteSuggestion>
)

@Serializable
data class AutocompleteSuggestion(
    val placeId: String,
    val description: String
)

@Serializable
data class PlaceDetailsResponse(
    val lat: Double,
    val lon: Double
)
