package com.avgangsplaneraren.app.data.trafikverket

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Schemat för `assets/rastplatser.json` — det format som konverterings-
 * skriptet `tools/convert_rastplatser.py` producerar från Trafikverkets
 * GeoPackage-export. Fälten motsvarar 1:1 [RestAreaEntity].
 */
@Serializable
data class RestAreaJson(
    val id: String,
    val namn: String? = null,
    val latitud: Double,
    val longitud: Double,
    val vagnummer: String? = null,
    val harBord: Boolean = false,
    val harBank: Boolean = false,
    val harToalett: Boolean = false,
    val harSoptunna: Boolean = false,
    val handikappanpassad: Boolean = false
)

private fun RestAreaJson.toEntity() = RestAreaEntity(
    id = id,
    namn = namn,
    latitud = latitud,
    longitud = longitud,
    vagnummer = vagnummer,
    harBord = harBord,
    harBank = harBank,
    harToalett = harToalett,
    harSoptunna = harSoptunna,
    handikappanpassad = handikappanpassad
)

/**
 * Fyller [TrafikverketDatabase] med rastplatser från `assets/rastplatser.json`
 * vid första appstart. Gör ingenting om databasen redan innehåller data,
 * så det här är billigt att anropa varje gång appen startar.
 *
 * Byt ut `rastplatser.json` mot en ny export (se `tools/convert_rastplatser.py`)
 * för att uppdatera rastplatsdatan — inkrementera gärna [DATA_VERSION] samtidigt
 * så att en ny appversion vet att seeda om.
 */
object TrafikverketDataSeeder {

    private const val ASSET_PATH = "rastplatser.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded(context: Context, dao: RestAreaDao) {
        if (dao.count() > 0) return

        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val entries = json.decodeFromString<List<RestAreaJson>>(raw)
        dao.insertAll(entries.map { it.toEntity() })
    }
}
