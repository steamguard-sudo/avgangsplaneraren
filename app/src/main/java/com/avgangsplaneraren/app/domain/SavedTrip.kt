package com.avgangsplaneraren.app.domain

data class SavedTrip(
    val id: Long = 0,
    val label: String,
    val fromPlace: String,
    val fromCoordinates: Coordinates,
    val toPlace: String,
    val toCoordinates: Coordinates,
    val bufferMinutes: Int,
    val minutesPerBreak: Int,
    val onlyStopsWithTableAndBench: Boolean,
    val showOvernightSpots: Boolean,
    val includeCaravanSites: Boolean,
    val includeCampSites: Boolean,
    val campingStopHours: Int,
    val campingStopExtraMinutes: Int,
    val notifyEnabled: Boolean,
    val notifyMinutesBefore: Int
)
