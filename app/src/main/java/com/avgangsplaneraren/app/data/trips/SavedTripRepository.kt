package com.avgangsplaneraren.app.data.trips

import com.avgangsplaneraren.app.domain.Coordinates
import com.avgangsplaneraren.app.domain.SavedTrip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SavedTripRepository(private val dao: SavedTripDao) {

    fun observeAll(): Flow<List<SavedTrip>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun save(trip: SavedTrip) {
        dao.insert(trip.toEntity(savedAtMillis = System.currentTimeMillis()))
    }

    suspend fun delete(trip: SavedTrip) {
        dao.delete(trip.toEntity(savedAtMillis = 0))
    }
}

private fun SavedTripEntity.toDomain() = SavedTrip(
    id = id,
    label = label,
    fromPlace = fromPlace,
    fromCoordinates = Coordinates(fromLat, fromLon),
    toPlace = toPlace,
    toCoordinates = Coordinates(toLat, toLon),
    bufferMinutes = bufferMinutes,
    minutesPerBreak = minutesPerBreak,
    onlyStopsWithTableAndBench = onlyStopsWithTableAndBench,
    showOvernightSpots = showOvernightSpots,
    includeCaravanSites = includeCaravanSites,
    includeCampSites = includeCampSites,
    campingStopHours = campingStopHours,
    campingStopExtraMinutes = campingStopExtraMinutes,
    notifyEnabled = notifyEnabled,
    notifyMinutesBefore = notifyMinutesBefore
)

private fun SavedTrip.toEntity(savedAtMillis: Long) = SavedTripEntity(
    id = id,
    label = label,
    fromPlace = fromPlace,
    fromLat = fromCoordinates.lat,
    fromLon = fromCoordinates.lon,
    toPlace = toPlace,
    toLat = toCoordinates.lat,
    toLon = toCoordinates.lon,
    bufferMinutes = bufferMinutes,
    minutesPerBreak = minutesPerBreak,
    onlyStopsWithTableAndBench = onlyStopsWithTableAndBench,
    showOvernightSpots = showOvernightSpots,
    includeCaravanSites = includeCaravanSites,
    includeCampSites = includeCampSites,
    campingStopHours = campingStopHours,
    campingStopExtraMinutes = campingStopExtraMinutes,
    notifyEnabled = notifyEnabled,
    notifyMinutesBefore = notifyMinutesBefore,
    savedAtMillis = savedAtMillis
)
