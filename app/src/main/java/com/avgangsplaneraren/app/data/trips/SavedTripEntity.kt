package com.avgangsplaneraren.app.data.trips

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_trips")
data class SavedTripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val fromPlace: String,
    val fromLat: Double,
    val fromLon: Double,
    val toPlace: String,
    val toLat: Double,
    val toLon: Double,
    val bufferMinutes: Int,
    val minutesPerBreak: Int,
    val onlyStopsWithTableAndBench: Boolean,
    val showOvernightSpots: Boolean,
    val includeCaravanSites: Boolean,
    val includeCampSites: Boolean,
    val campingStopHours: Int,
    val campingStopExtraMinutes: Int,
    val notifyEnabled: Boolean,
    val notifyMinutesBefore: Int,
    val savedAtMillis: Long
)
