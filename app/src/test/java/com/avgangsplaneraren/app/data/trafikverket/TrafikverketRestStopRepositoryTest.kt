package com.avgangsplaneraren.app.data.trafikverket

import com.avgangsplaneraren.app.domain.Coordinates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fejkad DAO utan Room/SQLite, så testet kan köras som ett vanligt JVM-
 * enhetstest utan Android-instrumentering.
 */
private class FakeRestAreaDao(private val all: List<RestAreaEntity>) : RestAreaDao {
    override suspend fun findInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<RestAreaEntity> = all.filter {
        it.latitud in minLat..maxLat && it.longitud in minLon..maxLon
    }
}

class TrafikverketRestStopRepositoryTest {

    private val nearby = RestAreaEntity(
        id = "1", namn = "Rastplats Nära", latitud = 57.80, longitud = 14.20,
        vagnummer = "E4", harBord = true, harBank = true, harToalett = false,
        harSoptunna = true, handikappanpassad = false
    )
    private val farAway = RestAreaEntity(
        id = "2", namn = "Rastplats Långt bort", latitud = 65.0, longitud = 22.0,
        vagnummer = "E4", harBord = true, harBank = true, harToalett = true,
        harSoptunna = true, handikappanpassad = true
    )

    @Test
    fun `returnerar bara rastplatser inom radien, sorterat pa avstand`() = runBlocking {
        val dao = FakeRestAreaDao(listOf(nearby, farAway))
        val repo = TrafikverketRestStopRepository(dao, radiusKm = 20.0)

        val result = repo.candidatesNear(Coordinates(57.7826, 14.1618), distanceFromStartKm = 100)

        assertEquals(1, result.size)
        assertEquals("Rastplats Nära", result.first().name)
    }

    @Test
    fun `mappar bord- och bankattribut korrekt`() = runBlocking {
        val dao = FakeRestAreaDao(listOf(nearby))
        val repo = TrafikverketRestStopRepository(dao, radiusKm = 20.0)

        val result = repo.candidatesNear(Coordinates(57.7826, 14.1618), distanceFromStartKm = 100)

        assertTrue(result.first().hasTable)
        assertTrue(result.first().hasBench)
        assertTrue(!result.first().hasToilet)
    }
}
