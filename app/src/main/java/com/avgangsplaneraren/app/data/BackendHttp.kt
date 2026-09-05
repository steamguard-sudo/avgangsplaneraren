package com.avgangsplaneraren.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Delad HTTP-uppsättning för alla backend-anrop (rutt, platser, övernattning,
 * laddplatser).
 *
 * Tidigare byggde varje repository sin egen [Retrofit] utan att sätta någon
 * [OkHttpClient] — vilket gav OkHttps default: **10 sekunders** read-timeout
 * och ingen total call-timeout. Backenden (Render free tier) behöver ofta
 * 20–45 sekunder på ett icke-cachat anrop: `/overnight` slår mot Overpass
 * live, och `/route` är långsam vid kallstart. Med 10 s hann anropen aldrig
 * klart — appen fick `SocketTimeoutException`, tolkade det som "inga träffar"
 * och föll dessutom **tyst** tillbaka på `RouteEstimator` (rak linje) för
 * rutten, vilket i sin tur gjorde att rastplatssökningen missade allt.
 *
 * Timeouterna här är medvetet generösa. [callTimeout] är ändå ett tak, så att
 * ett hängande anrop (t.ex. en död Overpass-spegel) failar i stället för att
 * ligga kvar för evigt.
 */
object BackendHttp {

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
