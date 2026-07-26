package com.avgangsplaneraren.app.data.trafikverket

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Vanlig, tom Room-databas som skapas på enheten och fylls vid första start
 * via [TrafikverketDataSeeder], som läser en bunten JSON-fil i `assets/`.
 *
 * Tidigare version av det här skelettet försökte leverera en färdigbyggd
 * SQLite-binärfil via `createFromAsset(...)`, men Room kräver då att
 * schemat (inklusive Rooms interna metadata-tabell) matchar exakt vad Room
 * själv skulle ha genererat – vilket är krångligt att producera från ett
 * fristående konverteringsskript. Att i stället låta Room skapa databasen
 * och seeda den med enkla INSERT-satser från JSON är betydligt mer robust
 * och lika snabbt i praktiken (rastplatser är några tusen rader, inte
 * miljontals).
 */
@Database(entities = [RestAreaEntity::class], version = 1, exportSchema = false)
abstract class TrafikverketDatabase : RoomDatabase() {

    abstract fun restAreaDao(): RestAreaDao

    companion object {
        private const val DB_NAME = "rastplatser.db"

        @Volatile
        private var instance: TrafikverketDatabase? = null

        fun getInstance(context: Context): TrafikverketDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrafikverketDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
