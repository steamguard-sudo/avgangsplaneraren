package com.avgangsplaneraren.app.data.directions

import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.PlaceProvider
import com.avgangsplaneraren.app.domain.PlaceSuggestion
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

/**
 * Riktig implementation av [PlaceProvider], via backend/server.js (som i
 * sin tur anropar Googles Places API och cachar svaren). Om anropet
 * misslyckas returneras en tom lista/kastas ett fel som UI-lagret fångar
 * och visar som "kunde inte söka just nu" — ingen offline-fallback här,
 * till skillnad från ruttdelen, eftersom en gissad koordinat för en
 * felstavad ort skulle kunna leda resenären fel.
 */
class GooglePlacesRepository(baseUrl: String) : PlaceProvider {

    private val api: BackendPlacesApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .asConverterFactory("application/json".toMediaType())
        )
        .build()
        .create(BackendPlacesApi::class.java)

    override suspend fun autocomplete(query: String): List<PlaceSuggestion> {
        if (query.isBlank()) return emptyList()
        return api.autocomplete(query).suggestions.map {
            PlaceSuggestion(placeId = it.placeId, description = it.description)
        }
    }

    override suspend fun details(placeId: String): Coordinates {
        val response = api.details(placeId)
        return Coordinates(lat = response.lat, lon = response.lon)
    }
}
