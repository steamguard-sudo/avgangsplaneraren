package com.avgangsplaneraren.app.data.directions

import android.util.Log
import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.RouteInfo
import com.avgangsplaneraren.app.domain.RouteProvider
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

/**
 * Riktig implementation av [RouteProvider]. Anropar backend/server.js
 * (som i sin tur anropar Googles Routes API och cachar svaret), avkodar
 * polylinen till [Coordinates], och faller tillbaka på [RouteEstimator]
 * om anropet misslyckas — t.ex. ingen nätanslutning eller backend nere.
 *
 * @param baseUrl bas-URL till ert backend, t.ex. "https://ert-backend.example.com/".
 *   Sätt via en byggkonfiguration (se README) — hårdkoda den aldrig i klartext
 *   i produktionskod utan att gå via t.ex. `BuildConfig`.
 */
class GoogleRoutesRepository(
    baseUrl: String,
    private val fallback: RouteProvider = RouteEstimator()
) : RouteProvider {

    private val api: BackendRoutesApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .asConverterFactory("application/json".toMediaType())
        )
        .build()
        .create(BackendRoutesApi::class.java)

    override suspend fun getRoute(from: Coordinates, to: Coordinates): RouteInfo {
        return try {
            val response = api.getRoute(from.lat, from.lon, to.lat, to.lon)
            RouteInfo(
                distanceKm = response.distanceKm,
                driveMinutes = response.driveMinutes,
                polyline = PolylineDecoder.decode(response.encodedPolyline)
            )
        } catch (e: Exception) {
            Log.w(
                "GoogleRoutesRepository",
                "Kunde inte hämta rutt från backend, faller tillbaka på uppskattning",
                e
            )
            fallback.getRoute(from, to)
        }
    }
}
