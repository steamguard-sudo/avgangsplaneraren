package com.avgangsplaneraren.app.data.trafikverket

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RestAreaDao {

    /**
     * Grovfiltrerar rastplatser inom en rektangel (bounding box) runt en
     * koordinat. SQLite/Room saknar inbyggt stöd för riktig geo-sökning,
     * så det här steget tar bara bort det stora flertalet som uppenbart
     * ligger för långt bort – exakt avstånd (haversine) räknas sedan ut i
     * Kotlin i [TrafikverketRestStopRepository], på den mindre mängd
     * kandidater som blir kvar.
     */
    @Query(
        """
        SELECT * FROM rastplats
        WHERE latitud BETWEEN :minLat AND :maxLat
        AND longitud BETWEEN :minLon AND :maxLon
        """
    )
    suspend fun findInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<RestAreaEntity>

    @Query("SELECT COUNT(*) FROM rastplats")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RestAreaEntity>)
}
