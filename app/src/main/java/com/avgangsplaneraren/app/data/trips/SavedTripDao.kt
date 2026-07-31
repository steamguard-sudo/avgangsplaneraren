package com.avgangsplaneraren.app.data.trips

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedTripDao {

    @Query("SELECT * FROM saved_trips ORDER BY savedAtMillis DESC")
    fun observeAll(): Flow<List<SavedTripEntity>>

    @Insert
    suspend fun insert(trip: SavedTripEntity): Long

    @Delete
    suspend fun delete(trip: SavedTripEntity)
}
