package com.avgangsplaneraren.app.data.trips

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedTripEntity::class], version = 1, exportSchema = false)
abstract class SavedTripDatabase : RoomDatabase() {

    abstract fun savedTripDao(): SavedTripDao

    companion object {
        private const val DB_NAME = "saved_trips.db"

        @Volatile
        private var instance: SavedTripDatabase? = null

        fun getInstance(context: Context): SavedTripDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SavedTripDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
