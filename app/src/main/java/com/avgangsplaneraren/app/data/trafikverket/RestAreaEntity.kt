package com.avgangsplaneraren.app.data.trafikverket

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lokal representation av en rastplats, byggd från Trafikverkets NVDB-
 * dataprodukt "Rastplats" (öppna data, licens CC0).
 *
 * Denna tabell fylls INTE av appen vid körning – den byggs i förväg genom
 * att konvertera Trafikverkets GeoPackage-fil (hämtad via Datautbytes-
 * portalen eller Lastkajen) till SQLite med t.ex. `ogr2ogr`, och bunta
 * resultatet som en förifylld Room-databas i `assets/`. Se README och
 * teknisk-plan-avgangsplaneraren.md, avsnitt 3.2, för hela flödet.
 *
 * Fältnamnen följer i stort NVDB:s egen terminologi för utrustning på en
 * rastplats.
 */
@Entity(tableName = "rastplats")
data class RestAreaEntity(
    @PrimaryKey val id: String,
    val namn: String?,
    val latitud: Double,
    val longitud: Double,
    val vagnummer: String?,
    val harBord: Boolean,
    val harBank: Boolean,
    val harToalett: Boolean,
    val harSoptunna: Boolean,
    val handikappanpassad: Boolean
)
