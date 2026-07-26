package com.avgangsplaneraren.app.domain

/**
 * Enkel koordinat (WGS84). Ligger i domänlagret eftersom både ruttberäkning
 * (data/directions) och rastplatssökning (data/trafikverket) behöver den,
 * utan att någon av dem ska behöva känna till den andra.
 */
data class Coordinates(val lat: Double, val lon: Double)
